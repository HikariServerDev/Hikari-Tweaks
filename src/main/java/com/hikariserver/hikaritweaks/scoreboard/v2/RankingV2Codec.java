package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// hikariscoreboard:ranking_v2 のバイト列 ⇄ RankingV2Message 変換。
//
// ★ MC 非依存。PacketByteBuf を使わず byte[] を直接読み書きする。
//   理由は 2 つある。
//   1. docs/ranking-v2-protocol.md §6 が「コーデックは MC に依存しない純ロジックへ
//      切り出してユニットテストを書く」ことを要求している。
//      PacketByteBuf を触ると単体テストが動かない。
//   2. §5.3 が「壊れたパケットで切断されないこと」を要求している。
//      byte[] を自前で読めば「残りバイト数」を毎回確認でき、
//      どこで打ち切られたかを確実に検出できる。
//
// エンコーディングは PacketByteBuf の標準実装と**バイト単位で同一**にすること（§2）。
//   varint … 7bit / バイト・最上位ビットが継続フラグ・最大 5 バイト
//   string … varint(UTF-8 バイト長) + UTF-8 バイト列
//   long   … 8 バイトのビッグエンディアン
//   uuid   … most significant long → least significant long の 16 バイト固定
public final class RankingV2Codec {

    // メッセージ種別バイト（§2）
    public static final int TYPE_HIDE     = 0x00;
    public static final int TYPE_SNAPSHOT = 0x01;
    public static final int TYPE_DELTA    = 0x02;

    // string フィールドの最大文字数（§2 の string(N) の N）
    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_NAME_LENGTH  = 64;

    // count を信用して確保する要素数の上限。
    // 壊れたサーバーが count = 20 億を送ってきても
    // ArrayList の初期確保で OOM しないようにするためのガード。
    // ここで**行を打ち切ってはいけない**
    //（打ち切りは RankingTable の 512 件ガードが担当する。§3.8）。
    private static final int INITIAL_CAPACITY_CAP = 256;

    // インスタンス化を禁止するプライベートコンストラクタ
    private RankingV2Codec() {}

    // パケットが仕様どおりに読めなかったことを表す例外。
    // 受信側はこれを捕まえてパケットを捨てるだけにする（§5.3）。
    public static final class MalformedPacketException extends RuntimeException {
        public MalformedPacketException(String message) { super(message); }
    }

    // ── デコード ─────────────────────────────────────────────

    // ペイロード全体を 1 メッセージとして読む。
    // 読めなければ MalformedPacketException を投げる（呼び出し側が捨てる）。
    //
    // 末尾に余ったバイトは**無視する**。§3.9 は「書く側が余分なバイトを足すな」
    // という規約であって、読む側が弾く必要は無い。むしろ将来サーバーが
    // フィールドを足したとき、弾くと旧クライアントがパケットごと落としてしまう。
    public static RankingV2Message decode(byte[] payload) {
        if (payload == null) throw new MalformedPacketException("payload is null");
        Reader r = new Reader(payload);

        // §2「先頭 1 バイトがメッセージ種別。将来の拡張のためここを必ず読むこと。」
        int type = r.readByte() & 0xFF;
        switch (type) {
            case TYPE_HIDE:
                // HIDE は 1 バイトで完結する。以降に何も書かれていない。
                return RankingV2Message.hide();
            case TYPE_SNAPSHOT: {
                int    boardId     = r.readVarInt();
                String title       = r.readString(MAX_TITLE_LENGTH);
                long   serverTotal = r.readLong();
                return RankingV2Message.snapshot(boardId, title, serverTotal, r.readRows());
            }
            case TYPE_DELTA: {
                int  boardId     = r.readVarInt();
                long serverTotal = r.readLong();
                List<RankingRow> upserts = r.readRows();

                int removeCount = r.readVarInt();
                if (removeCount < 0) {
                    throw new MalformedPacketException("negative removeCount: " + removeCount);
                }
                List<UUID> removals = new ArrayList<>(Math.min(removeCount, INITIAL_CAPACITY_CAP));
                for (int i = 0; i < removeCount; i++) {
                    removals.add(r.readUuid());
                }
                return RankingV2Message.delta(boardId, serverTotal, upserts, removals);
            }
            default:
                // 知らない種別。将来サーバーが種別を足したときにここへ来る。
                throw new MalformedPacketException("unknown message type: 0x" + Integer.toHexString(type));
        }
    }

    // ── エンコード ───────────────────────────────────────────

    // メッセージをバイト列にする。
    //
    // クライアントは v2 を送信しないので実運用では使わないが、
    // §6 が要求する「書き込み → 読み込みの往復」テストを
    // **本番のデコーダに対して**行うために必要なので main に置いている。
    // ここを消すと往復テストがテスト専用エンコーダ相手の自己満足になる。
    public static byte[] encode(RankingV2Message msg) {
        Writer w = new Writer();
        w.writeByte(msg.type().wireId());
        switch (msg.type()) {
            case HIDE:
                // 何も書かない（§2）。末尾に 1 バイトも足さないこと（§3.9）。
                break;
            case SNAPSHOT:
                w.writeVarInt(msg.boardId());
                w.writeString(msg.title(), MAX_TITLE_LENGTH);
                w.writeLong(msg.serverTotal());
                w.writeRows(msg.rows());
                break;
            case DELTA:
                w.writeVarInt(msg.boardId());
                w.writeLong(msg.serverTotal());
                w.writeRows(msg.rows());
                w.writeVarInt(msg.removals().size());
                for (UUID id : msg.removals()) {
                    w.writeUuid(id);
                }
                break;
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

        // 1 バイト読む。足りなければ打ち切りとして例外を投げる。
        byte readByte() {
            if (pos >= buf.length) {
                throw new MalformedPacketException("truncated packet at offset " + pos);
            }
            return buf[pos++];
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

        // most significant long → least significant long の 16 バイト固定
        UUID readUuid() {
            long most  = readLong();
            long least = readLong();
            return new UUID(most, least);
        }

        // PacketByteBuf.readString(maxLength) 相当。
        //
        // バイト長の上限は maxLength * 4 で見る。MC 1.20.5 以降の書き込み側は
        // maxLength * 3 までしか出さないが、**読み込みは緩いほうに合わせる**。
        // 厳しくしても壊れたサーバーを弾けるだけで、正当なパケットを落とす危険が増える。
        // 文字数（String.length()）は検査しない。これは書き込み側の不変条件であって、
        // 読み込み側で弾いても得るものが無い。
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

        // varint count + count 回の {uuid, string(64), long}
        List<RankingRow> readRows() {
            int count = readVarInt();
            if (count < 0) throw new MalformedPacketException("negative row count: " + count);
            // count は信用せず初期確保だけ制限する。
            // 巨大な count はループ内の readByte() が「打ち切り」で落とすので、
            // ここで件数を切る必要は無い（§3.8：200 で切ってはいけない）。
            List<RankingRow> rows = new ArrayList<>(Math.min(count, INITIAL_CAPACITY_CAP));
            for (int i = 0; i < count; i++) {
                UUID   id    = readUuid();
                String name  = readString(MAX_NAME_LENGTH);
                long   value = readLong();
                rows.add(new RankingRow(id, name, value));
            }
            return rows;
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

        void writeUuid(UUID id) {
            writeLong(id.getMostSignificantBits());
            writeLong(id.getLeastSignificantBits());
        }

        // バイト長の上限は maxLength * 3 で見る。
        // MC 1.20.5 以降の readString はここまでしか受け付けないので、
        // **一番厳しい読み手**に合わせて書く。
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

        void writeRows(List<RankingRow> rows) {
            writeVarInt(rows.size());
            for (RankingRow row : rows) {
                writeUuid(row.playerId());
                writeString(row.name(), MAX_NAME_LENGTH);
                writeLong(row.value());
            }
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }
    }
}
