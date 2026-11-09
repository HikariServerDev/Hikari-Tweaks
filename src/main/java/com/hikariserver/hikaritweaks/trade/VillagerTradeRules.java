package com.hikariserver.hikaritweaks.trade;

import java.util.ArrayList;
import java.util.List;

public final class VillagerTradeRules {
    private VillagerTradeRules() {}

    public static boolean scansAllOffers(int offerIndex, int offerCount) {
        return offerIndex <= 0 || offerIndex >= offerCount;
    }

    public static int[] depositPlan(int[] counts, boolean[] sameItem, int stackMax, int amount) {
        if (amount <= 0 || stackMax <= 0) {
            return null;
        }
        int remaining = amount;
        List<Integer> clicks = new ArrayList<>();

        for (int i = counts.length - 1; i >= 0 && remaining > 0; i--) {
            if (counts[i] > 0 && sameItem[i] && counts[i] < stackMax) {
                clicks.add(i);
                remaining -= Math.min(remaining, stackMax - counts[i]);
            }
        }
        for (int i = counts.length - 1; i >= 0 && remaining > 0; i--) {
            if (counts[i] == 0) {
                clicks.add(i);
                remaining -= Math.min(remaining, stackMax);
            }
        }
        if (remaining > 0) {
            return null;
        }

        int[] out = new int[clicks.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = clicks.get(i);
        }
        return out;
    }
}
