package com.hikariserver.hikaritweaks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemPickerRules {
    public static final int CELL = 18;

    private ItemPickerRules() {}

    public static boolean matches(String query, String id, String name) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        String lowerId = id.toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(q)
                || lowerId.contains(q)
                || lowerId.contains(q.replace(' ', '_'));
    }

    public static List<String> toggle(List<String> ids, String id) {
        List<String> out = new ArrayList<>(ids);
        if (out.contains(id)) {
            out.removeIf(id::equals);
        } else {
            out.add(id);
        }
        return out;
    }

    public static int columns(int areaWidth) {
        return Math.max(1, areaWidth / CELL);
    }

    public static int rowCount(int count, int columns) {
        if (count <= 0) return 0;
        return (count + columns - 1) / columns;
    }

    public static int rowAt(double y, int top, int visibleRows) {
        if (y < top) return -1;
        int row = (int) ((y - top) / CELL);
        return row < visibleRows ? row : -1;
    }

    public static int columnAt(double x, int left, int columns) {
        if (x < left) return -1;
        int column = (int) ((x - left) / CELL);
        return column < columns ? column : -1;
    }
}
