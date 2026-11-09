package com.hikariserver.hikaritweaks.warning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DurabilityWarningState {
    private static final class Entry {
        final String identity;
        final int damage;

        Entry(String identity, int damage) {
            this.identity = identity;
            this.damage = damage;
        }
    }

    private final Map<Integer, Entry> tracked = new HashMap<>();
    private final Set<Integer> seenThisTick = new HashSet<>();

    public void beginTick() {
        seenThisTick.clear();
    }

    public boolean offer(int slot, String identity, int damage) {
        if (!seenThisTick.add(slot)) return false;

        Entry prev = tracked.put(slot, new Entry(identity, damage));
        if (prev == null) return true;
        if (!prev.identity.equals(identity)) return true;
        return damage > prev.damage;
    }

    public void endTick() {
        tracked.keySet().retainAll(seenThisTick);
    }

    public void clear() {
        tracked.clear();
        seenThisTick.clear();
    }

    public boolean isTracked(int slot) {
        return tracked.containsKey(slot);
    }

    public int trackedCount() {
        return tracked.size();
    }

    public static int threshold(int maxDamage) {
        return Math.max(1, (int) Math.ceil(maxDamage * 0.01));
    }

    public static boolean inWarningState(int maxDamage, int damage) {
        return (maxDamage - damage) <= threshold(maxDamage);
    }

    public static int remainingPercent(int remaining, int maxDamage) {
        if (maxDamage <= 0) return 0;
        return Math.max(0, (int) Math.ceil((remaining * 100.0) / maxDamage));
    }
}
