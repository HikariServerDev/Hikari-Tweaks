package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.IdCompat;
import com.hikariserver.hikaritweaks.compat.NetCompat;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingRow;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingTable;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingV2Codec;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingV2Message;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// hikariscoreboard:ranking_v2（差分プロトコル）の受信口。
// バイト仕様と不変条件は docs/ranking-v2-protocol.md を参照。
//
// ┌─ このクラスが守っている約束 ───────────────────────────────┐
// │ ・v1（ranking_data）の受信経路には一切触らない。               │
// │   サーバーが旧版のままの構成があり得るため（§5.1）。           │
// │ ・チャンネルを登録していること自体がサーバーの判定材料である    │
// │   （§1）。登録は必ず v1 と同じタイミングで行う。               │
// │   ScoreboardPacketClient.register() から呼ばれること。         │
// │ ・パースは例外を捕まえてパケットを捨てるだけにする（§5.3）。    │
// │   v1（ScoreboardPacketClient）も同じ形で捕まえている。          │
// │   外へ出すと netty スレッドで切断される。片方だけ直さないこと。 │
// └────────────────────────────────────────────────────────────┘
public final class RankingV2Client {

    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/ranking_v2");

    // v2 チャンネル。v1 の RANKING_DATA は ScoreboardPacketClient 側にある。
    private static final Identifier RANKING_V2 = IdCompat.of("hikariscoreboard", "ranking_v2");

    // 壊れたパケットのログを絞る間隔（ナノ秒）。
    // 壊れたサーバーが毎 tick 送ってくるとログが埋まるため。
    private static final long MALFORMED_LOG_INTERVAL_NANOS = 5_000_000_000L;

    // テーブル本体。更新も参照も MC スレッド上でのみ行う
    //（受信スレッドからは client.execute() 経由で入る）。
    private static final RankingTable TABLE = new RankingTable();

    // v2 メッセージを 1 通でも受け取ったか。
    // true になったらこのセッションでは v1 のキャッシュを見ない。
    // サーバーは「同じプレイヤーへ v1 と v2 を両方送ってはならない」（§1）ので、
    // 両方を混ぜて描くと HUD が二重更新される。
    private static volatile boolean engaged = false;

    private static long lastMalformedLogNanos = Long.MIN_VALUE;

    // インスタンス化を禁止するプライベートコンストラクタ
    private RankingV2Client() {}

    // 受信ハンドラを登録する。ScoreboardPacketClient.register() から呼ぶこと。
    public static void register() {
        TABLE.setWarningSink(LOGGER::warn);

        NetCompat.registerReceiver(RANKING_V2, (client, buf) -> {
            RankingV2Message message;
            try {
                // ペイロードを byte[] に落としてから純ロジックのコーデックへ渡す。
                // buf はこのコールバックを抜けると無効になるのでここで読み切る。
                byte[] payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);
                message = RankingV2Codec.decode(payload);
            } catch (Exception e) {
                // §5.3: 例外を外へ出さない。出すと netty スレッドで落ちて切断される。
                logMalformed(e);
                return;
            }
            // テーブル更新は MC スレッドで行う（描画スレッドと同じにするため）
            client.execute(() -> apply(message));
        });
    }

    // MC スレッド上でメッセージをテーブルへ反映する
    private static void apply(RankingV2Message message) {
        engaged = true;
        RankingTable.ApplyResult result = TABLE.apply(message);
        if (result == RankingTable.ApplyResult.DROPPED_BOARD_MISMATCH) {
            // §3.1: boardId 不一致の DELTA は捨てる。
            // サーバーが boardId を上げた直後の入れ違いで正常に起きうるので警告にはしない。
            LOGGER.debug("ranking_v2: dropped DELTA for boardId {} (holding {})",
                    message.boardId(), TABLE.boardId());
        }
    }

    // 壊れたパケットのログ（間隔を空ける）
    private static void logMalformed(Exception e) {
        long now = System.nanoTime();
        if (now - lastMalformedLogNanos < MALFORMED_LOG_INTERVAL_NANOS) return;
        lastMalformedLogNanos = now;
        LOGGER.warn("ranking_v2: dropped a malformed packet: {}", e.toString());
    }

    // 切断・参加時に状態を捨てる
    public static void reset() {
        engaged = false;
        TABLE.reset();
    }

    // ── 参照（すべて MC スレッドから呼ぶこと）───────────────

    // v2 でサーバーと会話しているか
    public static boolean isEngaged() { return engaged; }

    // 表示できるボードを保持しているか（HIDE 後・初期状態は false）
    public static boolean hasBoard() { return TABLE.hasBoard() && !TABLE.isHidden(); }

    // §3.3 の規則で並んだ行。添字 + 1 が順位（§3.4）。
    public static List<RankingRow> sortedRows() { return TABLE.sorted(); }

    public static String title()       { return TABLE.title(); }
    public static long   serverTotal() { return TABLE.serverTotal(); }

    // ボードの同一性が変わるたびに増える値。描画側が補間状態を捨てる契機に使う。
    public static int generation() { return TABLE.generation(); }
}
