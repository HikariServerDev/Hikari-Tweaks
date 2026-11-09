package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RankingV2Codec {
    public static final int TYPE_HIDE     = 0x00;
    public static final int TYPE_SNAPSHOT = 0x01;
    public static final int TYPE_DELTA    = 0x02;

    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_NAME_LENGTH  = 64;

    private static final int INITIAL_CAPACITY_CAP = 256;

    private RankingV2Codec() {}

    public static final class MalformedPacketException extends RuntimeException {
        public MalformedPacketException(String message) { super(message); }
    }

    public static RankingV2Message decode(byte[] payload) {
        if (payload == null) throw new MalformedPacketException("payload is null");
        Reader r = new Reader(payload);

        int type = r.readByte() & 0xFF;
        switch (type) {
            case TYPE_HIDE:
                return RankingV2Message.hide();
            case TYPE_SNAPSHOT: {
                int    boardId     = r.readVarInt();
                String title       = r.readString(MAX_TITLE_LENGTH);
                long   serverTotal = r.readLong();
                List<RankingRow> rows = r.flagBots(r.readRows());
                return RankingV2Message.snapshot(boardId, title, serverTotal, rows);
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
                return RankingV2Message.delta(boardId, serverTotal, r.flagBots(upserts), removals);
            }
            default:
                throw new MalformedPacketException("unknown message type: 0x" + Integer.toHexString(type));
        }
    }

    public static byte[] encode(RankingV2Message msg) {
        Writer w = new Writer();
        w.writeByte(msg.type().wireId());
        switch (msg.type()) {
            case HIDE:
                break;
            case SNAPSHOT:
                w.writeVarInt(msg.boardId());
                w.writeString(msg.title(), MAX_TITLE_LENGTH);
                w.writeLong(msg.serverTotal());
                w.writeRows(msg.rows());
                w.writeBots(msg.rows());
                break;
            case DELTA:
                w.writeVarInt(msg.boardId());
                w.writeLong(msg.serverTotal());
                w.writeRows(msg.rows());
                w.writeVarInt(msg.removals().size());
                for (UUID id : msg.removals()) {
                    w.writeUuid(id);
                }
                w.writeBots(msg.rows());
                break;
        }
        return w.toByteArray();
    }

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) { this.buf = buf; }

        private int remaining() { return buf.length - pos; }

        byte readByte() {
            if (pos >= buf.length) {
                throw new MalformedPacketException("truncated packet at offset " + pos);
            }
            return buf[pos++];
        }

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

        long readLong() {
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (readByte() & 0xFFL);
            }
            return value;
        }

        UUID readUuid() {
            long most  = readLong();
            long least = readLong();
            return new UUID(most, least);
        }

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

        List<RankingRow> readRows() {
            int count = readVarInt();
            if (count < 0) throw new MalformedPacketException("negative row count: " + count);
            List<RankingRow> rows = new ArrayList<>(Math.min(count, INITIAL_CAPACITY_CAP));
            for (int i = 0; i < count; i++) {
                UUID   id    = readUuid();
                String name  = readString(MAX_NAME_LENGTH);
                long   value = readLong();
                rows.add(new RankingRow(id, name, value));
            }
            return rows;
        }

        List<RankingRow> flagBots(List<RankingRow> rows) {
            if (remaining() == 0) return rows;

            int count = readVarInt();
            if (count < 0) throw new MalformedPacketException("negative botCount: " + count);
            Set<UUID> bots = new HashSet<>(Math.min(count, INITIAL_CAPACITY_CAP) * 2);
            for (int i = 0; i < count; i++) {
                bots.add(readUuid());
            }
            if (bots.isEmpty()) return rows;

            List<RankingRow> flagged = new ArrayList<>(rows.size());
            for (RankingRow row : rows) {
                flagged.add(row.withBot(bots.contains(row.playerId())));
            }
            return flagged;
        }
    }

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

        void writeBots(List<RankingRow> rows) {
            int count = 0;
            for (RankingRow row : rows) {
                if (row.bot()) count++;
            }
            writeVarInt(count);
            for (RankingRow row : rows) {
                if (row.bot()) writeUuid(row.playerId());
            }
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }
    }
}
