package com.hikariserver.hikaritweaks.scoreboard.v1;

import com.hikariserver.hikaritweaks.scoreboard.PlayerListEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ScoreboardV1Codec {
    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_NAME_LENGTH  = 64;
    public static final int MAX_UUID_LENGTH  = 36;

    private static final int INITIAL_CAPACITY_CAP = 256;

    private ScoreboardV1Codec() {}

    public static final class MalformedPacketException extends RuntimeException {
        public MalformedPacketException(String message) { super(message); }
    }

    public static RankingV1Data decodeRanking(byte[] payload) {
        if (payload == null) throw new MalformedPacketException("payload is null");
        Reader r = new Reader(payload);

        if (r.readBoolean()) {
            return RankingV1Data.hide();
        }

        String title = r.readString(MAX_TITLE_LENGTH);
        List<RankingV1Entry> top = r.readEntries();

        long   serverTotal = r.readLong();
        int    selfRank    = r.readVarInt();
        long   selfValue   = r.readLong();
        String selfName    = r.readString(MAX_NAME_LENGTH);

        List<RankingV1Entry> full = r.hasRemaining() ? r.readEntries() : top;

        return RankingV1Data.of(title, top, full, serverTotal, selfRank, selfValue, selfName);
    }

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

    public static byte[] encodeRanking(RankingV1Data data) {
        Writer w = new Writer();
        w.writeBoolean(data.hidden());
        if (data.hidden()) {
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

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) { this.buf = buf; }

        private int remaining() { return buf.length - pos; }

        boolean hasRemaining() { return remaining() > 0; }

        byte readByte() {
            if (pos >= buf.length) {
                throw new MalformedPacketException("truncated packet at offset " + pos);
            }
            return buf[pos++];
        }

        boolean readBoolean() {
            return readByte() != 0;
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
