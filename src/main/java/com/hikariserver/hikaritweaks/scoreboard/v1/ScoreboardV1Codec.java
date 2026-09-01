package com.hikariserver.hikaritweaks.scoreboard.v1;

import com.hikariserver.hikaritweaks.scoreboard.PlayerListEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// hikariscoreboard の v1 チャンネル
//（ranking_data / player_list_response）のバイト列 ⇄ データ変換。
//
// ★ MC 非依存。PacketByteBuf を使わず byte[] を直接読み書きする。
//   理由は v2 の RankingV2Codec と同じで 2 つある。
//   1. PacketByteBuf を触ると Minecraft 抜きのユニットテストが動かない。
//   2. byte[] を自前で読めば「残りバイト数」を毎回確認でき、
//      どこで打ち切られたかを確実に検出できる。
//      PacketByteBuf は打ち切りを IndexOutOfBoundsException / DecoderException で
//      知らせてくるが、それが**どのスレッドで飛ぶか**が問題になる（下記）。
//
// ★ ここで投げた例外は必ず ScoreboardPacketClient が捕まえること。
//   受信コールバックの外へ例外を出すと以下の経路でセッションが終わる。
//     MC 1.20.5 未満 … Fabric の legacy ハンドラは netty のイベントループ上で走るため
//                      例外は exceptionCaught に落ちてサーバー切断になる。
//     MC 1.20.5 以降 … ハンドラがクライアントスレッドで走るためクラッシュレポートになる。
//   どちらも「壊れたパケット 1 通でセッションが終わる」ことに変わりはない。
//
// ★ ワイヤ仕様（バイト順）は絶対に変えないこと。サーバー（HikariScoreBoard）の
//   旧バージョンがそのまま動いている構成があり得る。
//
//   ranking_data:
//     boolean hidden
//     hidden == true ならここで終わり（以降は書かれない）
//     string(256) title
//     varint       count
//     count 回 { string(64) name; long value }
//     long         serverTotal
//     varint       selfRank
//     long         selfValue
//     string(64)   selfName
//     ここでバイトが残っていれば
//       varint       fullCount
//       fullCount 回 { string(64) name; long value }
//     残っていなければ full = top（旧サーバー互換）
//
//   player_list_response:
//     varint count
//     count 回 { string(36) uuid; string(64) displayName;
//                boolean isBot; boolean isBlocked }
//
// ★ エンコーダ（encodeRanking / encodePlayerList）はクライアントの実行時には
//   使わないが main に置いてある。§6 と同じ考え方で、
//   「書き込み → 読み込みの往復」テストを**本番のデコーダに対して**行うために要る。
//   ここを test 側へ移すとテスト専用エンコーダ相手の自己満足になる。
//
// ★ v2 の RankingV2Codec と読み書きの基本型（varint / string / long）は同じだが、
//   共有ヘルパにはしていない。v1 と v2 は別々のサーバー実装が相手の独立した
//   プロトコルで、v1 のバイト列はもう凍結されている。共有すると v2 側の都合の
//   変更が v1 のパース結果を黙って変えてしまう。
public final class ScoreboardV1Codec {

    // string フィールドの最大文字数（サーバー側の writeString(s, N) の N と一致させる）
    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_NAME_LENGTH  = 64;
    public static final int MAX_UUID_LENGTH  = 36;

    // count を信用して確保する要素数の上限。
    //
    // これが今回の不具合の本体だった。壊れた／途中で切れたパケットの varint は
    // 簡単に 19 億のような値になり、new ArrayList<>(count) がその場で
    // Object[1_900_000_000]（約 7.6GB）を確保しようとして OutOfMemoryError になる。
    // count 自体は信用しないまま初期確保だけを切る。
    //
    // ★ ここで**行を打ち切ってはいけない**。件数を切ると正当な巨大リストが
    //   黙って欠ける。巨大な count はループ内の readByte() が「打ち切り」として
    //   確実に落とすので、上限を設ける必要は無い。
    private static final int INITIAL_CAPACITY_CAP = 256;

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardV1Codec() {}

    // パケットが仕様どおりに読めなかったことを表す例外。
    // 受信側はこれを捕まえてパケットを捨てるだけにする。
    public static final class MalformedPacketException extends RuntimeException {
        public MalformedPacketException(String message) { super(message); }
    }

    // ── デコード ─────────────────────────────────────────────

    // ranking_data のペイロードを読む。読めなければ MalformedPacketException。
    //
    // 末尾に余ったバイトは無視する。将来サーバーがフィールドを足したときに
    // 旧クライアントがパケットごと落とさないようにするため。
    public static RankingV1Data decodeRanking(byte[] payload) {
        if (payload == null) throw new MalformedPacketException("payload is null");
        Reader r = new Reader(payload);

        // 非表示指示は先頭の boolean だけで完結する
        if (r.readBoolean()) {
            return RankingV1Data.hide();
        }

        String title = r.readString(MAX_TITLE_LENGTH);
        List<RankingV1Entry> top = r.readEntries();

        long   serverTotal = r.readLong();
        int    selfRank    = r.readVarInt();
        long   selfValue   = r.readLong();
        String selfName    = r.readString(MAX_NAME_LENGTH);

        // フルリストが含まれている場合は読み込む（なければ top を使う）。
        // 旧サーバーはここに何も書かない。
        List<RankingV1Entry> full = r.hasRemaining() ? r.readEntries() : top;

        return RankingV1Data.of(title, top, full, serverTotal, selfRank, selfValue, selfName);
    }

    // player_list_response のペイロードを読む。読めなければ MalformedPacketException。
    public static List<PlayerListEntry> decodePlayerList(byte[] payload) {
        if (payload == null) throw new MalformedPacketException("payload is null");
        Reader r = new Reader(payload);

        int count = r.readVarInt();
        if (count < 0) throw new MalformedPacketException("negative player count: " + count);
        List<PlayerListEntry> list = new ArrayList<>(Math.min(count, INITIAL_CAPACITY_CAP));
        for (int i = 0; i < count; i++) {
            String  uuid        = r.readString(MAX_UUID_LENGTH);
            String  displayName = r.readString(MAX_NAME_LENGTH);
            boolean isBot       = r.readBoolean();
            boolean isBlocked   = r.readBoolean();
            list.add(new PlayerListEntry(uuid, displayName, isBot, isBlocked));
        }
        return list;
    }

    // ── エンコード（テスト用。実行時は使わない）───────────────

    public static byte[] encodeRanking(RankingV1Data data) {
        Writer w = new Writer();
        w.writeBoolean(data.hidden());
        if (data.hidden()) {
            // 非表示指示は 1 バイトで完結する。末尾に 1 バイトも足さないこと。
            return w.toByteArray();
        }
        w.writeString(data.title(), MAX_TITLE_LENGTH);
        w.writeEntries(data.top());
        w.writeLong(data.serverTotal());
        w.writeVarInt(data.selfRank());
        w.writeLong(data.selfValue());
        w.writeString(data.selfName(), MAX_NAME_LENGTH);
        w.writeEntries(data.full());
        return w.toByteArray();
    }

    public static byte[] encodePlayerList(List<PlayerListEntry> list) {
        Writer w = new Writer();
        w.writeVarInt(list.size());
        for (PlayerListEntry e : list) {
            w.writeString(e.uuid(), MAX_UUID_LENGTH);
            w.writeString(e.displayName(), MAX_NAME_LENGTH);
            w.writeBoolean(e.isBot());
            w.writeBoolean(e.isBlocked());
        }
        return w.toByteArray();
    }

    // ── byte[] リーダ ────────────────────────────────────────

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) { this.buf = buf; }

        // 残りバイト数
        private int remaining() { return buf.length - pos; }

        // PacketByteBuf.isReadable() 相当
        boolean hasRemaining() { return remaining() > 0; }

        // 1 バイト読む。足りなければ打ち切りとして例外を投げる。
        byte readByte() {
            if (pos >= buf.length) {
                throw new MalformedPacketException("truncated packet at offset " + pos);
            }
            return buf[pos++];
        }

        // ByteBuf.readBoolean() と同じく 0 以外を true とみなす
        boolean readBoolean() {
            return readByte() != 0;
        }

        // PacketByteBuf.readVarInt() と同じ規則で読む（最大 5 バイト）
        int readVarInt() {
            int value = 0;
            int step  = 0;
            byte b;
            do {
                b = readByte();
                value |= (b & 0x7F) << (step++ * 7);
                if (step > 5) throw new MalformedPacketException("VarInt too big");
            } while ((b & 0x80) != 0);
            return value;
        }

        // 8 バイトのビッグエンディアン
        long readLong() {
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (readByte() & 0xFFL);
            }
            return value;
        }

        // PacketByteBuf.readString(maxLength) 相当。
        //
        // バイト長の上限は maxLength * 4 で見る。MC 1.20.5 以降の書き込み側は
        // maxLength * 3 までしか出さないが、読み込みは緩いほうに合わせる
        //（厳しくしても壊れたサーバーを弾けるだけで、正当なパケットを落とす危険が増える）。
        // 文字数（String.length()）は検査しない。これは書き込み側の不変条件。
        String readString(int maxLength) {
            int byteLength = readVarInt();
            if (byteLength < 0) {
                throw new MalformedPacketException("negative string length: " + byteLength);
            }
            if (byteLength > maxLength * 4) {
                throw new MalformedPacketException(
                        "string too long: " + byteLength + " > " + (maxLength * 4));
            }
            if (byteLength > remaining()) {
                throw new MalformedPacketException(
                        "truncated string: need " + byteLength + " bytes, have " + remaining());
            }
            String s = new String(buf, pos, byteLength, StandardCharsets.UTF_8);
            pos += byteLength;
            return s;
        }

        // varint count + count 回の {string(64), long}
        List<RankingV1Entry> readEntries() {
            int count = readVarInt();
            if (count < 0) throw new MalformedPacketException("negative entry count: " + count);
            List<RankingV1Entry> entries = new ArrayList<>(Math.min(count, INITIAL_CAPACITY_CAP));
            for (int i = 0; i < count; i++) {
                String name  = readString(MAX_NAME_LENGTH);
                long   value = readLong();
                entries.add(new RankingV1Entry(name, value));
            }
            return entries;
        }
    }

    // ── byte[] ライタ ────────────────────────────────────────

    private static final class Writer {
        private byte[] buf = new byte[64];
        private int size;

        private void ensure(int extra) {
            if (size + extra <= buf.length) return;
            int cap = buf.length;
            while (cap < size + extra) cap <<= 1;
            byte[] next = new byte[cap];
            System.arraycopy(buf, 0, next, 0, size);
            buf = next;
        }

        void writeByte(int value) {
            ensure(1);
            buf[size++] = (byte) value;
        }

        void writeBoolean(boolean value) {
            writeByte(value ? 1 : 0);
        }

        void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            writeByte(value);
        }

        void writeLong(long value) {
            ensure(8);
            for (int i = 7; i >= 0; i--) {
                buf[size++] = (byte) (value >>> (i * 8));
            }
        }

        // バイト長の上限は maxLength * 3 で見る（一番厳しい読み手に合わせる）
        void writeString(String s, int maxLength) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxLength * 3) {
                throw new MalformedPacketException("string too long to encode: " + bytes.length);
            }
            writeVarInt(bytes.length);
            ensure(bytes.length);
            System.arraycopy(bytes, 0, buf, size, bytes.length);
            size += bytes.length;
        }

        void writeEntries(List<RankingV1Entry> entries) {
            writeVarInt(entries.size());
            for (RankingV1Entry e : entries) {
                writeString(e.name(), MAX_NAME_LENGTH);
                writeLong(e.value());
            }
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }
    }
}
