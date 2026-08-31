package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.HudCompat;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.v2.ValueInterpolator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.UUID;

// カスタムスコアボード HUD 描画クラス。
//
// ┌─ 配置方針 ────────────────────────────────────────────┐
// │  positionX / positionY は画面幅・高さに対する % (0-100)  │
// │  positionX=100 かつ positionY=50 がデフォルト（右中央）   │
// │  スコアボードの右端が anchorX に、中央が anchorY に来る    │
// └──────────────────────────────────────────────────────┘
public final class ScoreboardHudRenderer {

    // 現在表示中のページ番号（0 始まり）
    private static int currentPage = 0;

    // 表示される数値の実時間補間（docs/ranking-v2-protocol.md §5.2）。
    // v2 経路の行だけが対象。v1 は uuid を運ばず行の同一性を名前でしか表せないため、
    // 補間すると集計由来の別名が付いたときに別人の状態を引き継いでしまう。
    private static final ValueInterpolator INTERPOLATOR = new ValueInterpolator();
    // 補間状態を捨てる契機を検出するための、直前に見たボード世代
    private static int lastBoardGeneration = Integer.MIN_VALUE;

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardHudRenderer() {}

    // ── ページ操作 ───────────────────────────────────────

    // 次のページへ移動する（最終ページを超えない）
    public static void nextPage() {
        ScoreboardView.Data data = ScoreboardView.current();
        if (data == null) return;
        int max = calcMaxPage(data.size(), ClientConfigManager.config.scoreboardPageSize);
        if (currentPage < max) currentPage++;
    }

    // 前のページへ移動する（0 未満にならない）
    public static void prevPage() {
        if (currentPage > 0) currentPage--;
    }

    // 先頭ページへリセットする
    public static void resetPage() { currentPage = 0; }

    // 現在のページ番号を返す
    public static int getCurrentPage() { return currentPage; }

    // 総エントリ数とページサイズから最大ページ番号（0 始まり）を返す
    public static int getMaxPage(int totalEntries, int pageSize) {
        return calcMaxPage(totalEntries, pageSize);
    }

    // 最大ページ番号を計算する内部ヘルパー
    private static int calcMaxPage(int total, int pageSize) {
        if (pageSize <= 0 || total <= pageSize) return 0;
        return (total - 1) / pageSize;
    }

    // ── 描画 ─────────────────────────────────────────────

    // インゲーム HUD に描画する。
    // MixinInGameHud から毎フレーム呼ばれる。
    //
    // ★ 補間の時計は System.nanoTime()（実時間・単調増加）だけである。
    //   InGameHud.render がくれる partial tick を引数に足さないこと。
    //   あれは tick 内の位相であって経過時間ではなく、
    //   tick レート変更・一時停止・サーバーラグで意味が変わる。
    //   docs/ranking-v2-protocol.md §5.2 が「tick 数やパケット数ではなく実時間で」と
    //   繰り返し書いているとおり、まさにそこが警告している失敗の仕方になる。
    public static void render(DrawCtx ctx) {
        ClientConfig cfg = ClientConfigManager.config;
        // カスタム HUD が無効なら何もしない
        if (!cfg.scoreboardCustomHud) return;

        // v1 / v2 のどちらから読むかは ScoreboardView が決める。
        // サーバーからの非表示指示（v1 の hide / v2 の HIDE）もそこで吸収している。
        ScoreboardView.Data data = ScoreboardView.current();
        if (data == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // F1（HUD 非表示）中はカスタムスコアボードも一緒に隠す。
        // MixinInGameHud は InGameHud.render の TAIL に注入しているため、
        // バニラ側の HUD 非表示処理をすり抜けて描画されてしまう。ここで明示的に弾く。
        if (mc.options.hudHidden) return;
        // F3 デバッグ画面表示中はスコアボードを非表示にする
        if (HudCompat.isDebugHudShown(mc)) return;

        TextRenderer tr      = mc.textRenderer;
        int scaledW          = mc.getWindow().getScaledWidth();
        int scaledH          = mc.getWindow().getScaledHeight();
        float scale          = cfg.scoreboardScale;

        // 総数を取得する。
        // ★ 値でフィルタしてはいけない（§3.7）。0 は PING / HEALTH / PLAYER_LEVEL では
        //   正当な値（レベル 0・死亡直後の体力 0・ローカル接続の ping 0）であり、
        //   何を出すかを決めるのはサーバーだけである。
        int total = data.size();
        if (total == 0) return;

        int pageSize = Math.max(1, cfg.scoreboardPageSize);
        int maxPage  = calcMaxPage(total, pageSize);
        // ランキング更新でエントリ数が変化してもページが範囲外にならないよう clamp するだけ
        // （resetPage() は呼ばず、可能な限り現在ページを維持する）
        if (currentPage > maxPage) currentPage = maxPage;

        // 表示するエントリ範囲を計算する
        int startIdx = currentPage * pageSize;
        int endIdx   = Math.min(startIdx + pageSize, total);
        int visible  = endIdx - startIdx;

        // ── 補間（表示する数字だけ）─────────────────────────
        // ボードが切り替わったら（別の stat になったら）補間状態を捨てる。
        // 採掘数 → 死亡回数のような無関係な数値へ数え下げ演出が出るのを防ぐ。
        int generation = RankingV2Client.generation();
        if (generation != lastBoardGeneration) {
            lastBoardGeneration = generation;
            INTERPOLATOR.reset();
        }

        long nowNanos = System.nanoTime();
        // 補間は**常時 ON**。設定項目は無い（§5.2）。
        // ON / OFF の見分けがつかない（跳び幅がほぼ常に +1 で、整数表示では
        // N と N+1 の間に描ける中間値が無い）ため設定ごと削除した。
        //
        // ただし「常時 ON」＝「無条件」ではない。下の key != null と
        // interpolatable() は残すこと。判定を消してはいけない理由は下に書く。
        //
        // 並び順と順位は**実値**で決まっている（data は既にソート済み）。
        // 補間するのは表示される数字だけなので、補間中に行が入れ替わってちらつかない。
        long[] shownValues = new long[visible];
        for (int i = 0; i < visible; i++) {
            int  idx  = startIdx + i;
            long real = data.value(idx);
            // v2 の行だけが補間キー（UUID）を持つ。v1 の行は UUID を持たないので
            // interpolationKey() が null を返し、実値をそのまま描く。
            UUID key  = data.interpolationKey(idx);
            shownValues[i] = (key != null)
                    ? INTERPOLATOR.display(key, real, nowNanos)
                    : real;
        }
        // 合計行も行と同じ補間器・同じ実時間の時計で進める（§5.2）。
        // HUD 上でいちばん大きい数字なので、行が滑らかに動いている横で
        // ここだけ飛ぶといちばん目立つ。
        //
        // §3.5: serverTotal が負のときは Total 行自体を出さない。
        // そのフレームは補間器に触らないので endFrame() が状態を捨て、
        // 次に Total 行が現れたときは補間ではなく即座に実値になる
        //（ページを開き直した直後も同じ）。
        //
        // ★ interpolatable() の判定を「常時 ON になったから」といって
        //   消してはいけない。補間するのは v2 経路のときだけ（行と同じ条件）。
        //   v1 は行が UUID を持たず補間できないので、ここを無条件にすると
        //   補間されない行の上で合計だけが滑って動く。
        //   それはこの判定を入れる原因になった不具合そのものであって、
        //   直そうとしている問題の裏返しになる。
        boolean showTotal  = cfg.scoreboardShowServerTotal && data.serverTotal() >= 0;
        long    realTotal  = data.serverTotal();
        long    shownTotal = (showTotal && data.interpolatable())
                ? INTERPOLATOR.displayTotal(realTotal, nowNanos)
                : realTotal;

        // このフレームで触らなかった行（テーブルから消えた行・別ページの行）と
        // 描かなかった合計行の状態を捨てる。呼ばないと状態が単調増加する。
        INTERPOLATOR.endFrame();

        // ── 幅の計算（スケール前の論理ピクセルで）──────────────
        String title    = data.title();
        int titleW      = tr.getWidth(title);
        int maxEntryW   = titleW;
        // 各エントリ行の最大幅を求める
        for (int i = 0; i < visible; i++) {
            int idx = startIdx + i;
            int globalRank = idx + 1;
            String rankStr  = String.format("%2d ", globalRank);
            String nameStr  = data.name(idx);
            // 補間中の数字と実値の**長い方**で幅を取る。
            // 補間値だけで測ると桁数が変わるたびに枠が伸び縮みしてちらつく。
            int scoreW = Math.max(
                    tr.getWidth(String.valueOf(data.value(idx))),
                    tr.getWidth(String.valueOf(shownValues[i])));
            int w = tr.getWidth(rankStr) + tr.getWidth(nameStr) + 4 + scoreW;
            if (w > maxEntryW) maxEntryW = w;
        }
        // ページ表示行の幅を考慮する
        if (maxPage > 0) {
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            int pw = tr.getWidth(pageLine);
            if (pw > maxEntryW) maxEntryW = pw;
        }
        // サーバートータル行の幅を考慮する。
        // 行と同じく実値と補間中の値の**長い方**で測る。
        // 補間値だけで測ると桁数が変わるたびに枠が伸び縮みしてちらつく。
        if (showTotal) {
            String totalLabel = "Total:";
            int totalValueW = Math.max(
                    tr.getWidth(String.valueOf(realTotal)),
                    tr.getWidth(String.valueOf(shownTotal)));
            int tw = tr.getWidth(totalLabel) + 4 + totalValueW;
            if (tw > maxEntryW) maxEntryW = tw;
        }

        // ボックスサイズを計算する（左右3pxずつパディング）
        int lineH    = 9;
        int boxW     = maxEntryW + 6;   // 左右3pxずつパディング
        int boxH     = (visible + 1) * lineH + 2; // +1 = タイトル行, +2 = 上下1px
        if (maxPage > 0) boxH += lineH; // ページ行
        // ★ 高さの条件は下の描画と**同じ showTotal** を使うこと。
        //   cfg.scoreboardShowServerTotal だけで数えると、§3.5 のセンチネル
        //   （serverTotal < 0 = Total 行を出すな。RankingTable の初期値でもある）のときに
        //   行は描かれないのに高さだけ 9px 多く数えてしまう。
        //   yStart = lAnchorY - boxH / 2 なので、盤面ごとアンカーより 4〜5px 上へずれる。
        if (showTotal) boxH += lineH; // サーバートータル行

        // ── アンカー座標（スケール後ピクセル）─────────────────
        int anchorX = (int)(scaledW * cfg.scoreboardPositionX / 100.0);
        int anchorY = (int)(scaledH * cfg.scoreboardPositionY / 100.0);

        // スケール変換をマトリクススタックに積む
        ctx.push();
        ctx.translate(anchorX, anchorY, 0);
        ctx.scale(scale, scale, 1.0f);
        ctx.translate(-anchorX / scale, -anchorY / scale, 0);

        // スケール後の論理座標でアンカーを再計算する
        int lAnchorX = (int)(anchorX / scale);
        int lAnchorY = (int)(anchorY / scale);

        // スコアボードの右端 = lAnchorX、縦中央 = lAnchorY
        int xEnd   = lAnchorX - 1;
        int xStart = xEnd - boxW;
        int yStart = lAnchorY - boxH / 2;

        // ── タイトル行 ──────────────────────────────────────
        int titleBgY = yStart;
        // ヘッダー背景を描画する
        ctx.fill(xStart, titleBgY, xEnd, titleBgY + lineH + 1,
                cfg.scoreboardHeaderColor);
        // タイトルテキストを中央揃えで描画する
        int titleX = xStart + (boxW - titleW) / 2;
        ctx.drawTextWithShadow(tr, title, titleX, titleBgY + 1, cfg.scoreboardTextColor);

        // ── サーバートータル行（タイトルの直下、エントリの上）──
        // §3.5: serverTotal が負のときは Total 行を出さない（v1 と同じ意味）
        int statsRowOffset = 0;
        if (showTotal) {
            int totalRowY = yStart + lineH + 1;
            ctx.fill(xStart, totalRowY, xEnd, totalRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String totalLabel = "Total:";
            // 補間後の値を描く（§5.2）。順位や表示可否の判定には実値を使っている。
            String totalValue = String.valueOf(shownTotal);
            // ラベルを左寄せ、数値を右寄せで描画する
            ctx.drawTextWithShadow(tr, totalLabel, xStart + 2, totalRowY, 0xFFAAAAAA);
            int totalValueX = xEnd - tr.getWidth(totalValue) - 2;
            ctx.drawTextWithShadow(tr, totalValue, totalValueX, totalRowY, cfg.scoreboardScoreColor);
            statsRowOffset = lineH;
        }

        // ── エントリ行 ──────────────────────────────────────
        for (int i = 0; i < visible; i++) {
            int idx = startIdx + i;
            // §3.4: 順位は並べ替えた後の添字 + 1。サーバーは順位を送らない。
            int globalRank = idx + 1;
            int rowY = yStart + (i + 1) * lineH + 1 + statsRowOffset;

            // エントリ行の背景を描画する
            ctx.fill(xStart, rowY, xEnd, rowY + lineH,
                    cfg.scoreboardBodyColor);

            // 自分かどうかを判定して色を変える。
            // v2 は uuid で判定する（§3.6）。v1 は表示名一致のままで、
            // 別名が付くと効かない既知の不具合がある（受信経路を変えないため据え置き）。
            boolean isSelf = data.isSelf(idx);

            // 順位を描画する（自分は強調色）
            String rankStr = String.format("%2d ", globalRank);
            int rankColor  = isSelf ? cfg.scoreboardSelfColor : 0xFFAAAAAA;
            ctx.drawTextWithShadow(tr, rankStr, xStart + 2, rowY, rankColor);

            // プレイヤー名を描画する（自分は強調色）
            int rankW   = tr.getWidth(rankStr);
            int nameColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardTextColor;
            ctx.drawTextWithShadow(tr, data.name(idx), xStart + 2 + rankW, rowY, nameColor);

            // スコア数値を右寄せで描画する（自分は強調色）
            String scoreStr = String.valueOf(shownValues[i]);
            int scoreX = xEnd - tr.getWidth(scoreStr) - 2;
            int scoreColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardScoreColor;
            ctx.drawTextWithShadow(tr, scoreStr, scoreX, rowY, scoreColor);
        }

        // ── ページ行 ────────────────────────────────────────
        // ページが複数ある場合のみページナビゲーション行を描画する
        if (maxPage > 0) {
            int pageRowY = yStart + (visible + 1) * lineH + 1 + statsRowOffset;
            ctx.fill(xStart, pageRowY, xEnd, pageRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            // ページ表示を中央揃えで描画する
            int pageLineX = xStart + (boxW - tr.getWidth(pageLine)) / 2;
            ctx.drawTextWithShadow(tr, pageLine, pageLineX, pageRowY, 0xFFAAAAAA);
        }

        // マトリクスを元に戻す
        ctx.pop();
    }
}
