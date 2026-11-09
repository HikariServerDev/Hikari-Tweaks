package com.hikariserver.hikaritweaks.restock;

public final class RestockRules {
    private RestockRules() {}

    public static int plannedMoveCount(int hotbarCount, int maxCount, int sourceCount) {
        int room = maxCount - hotbarCount;
        if (room <= 0 || sourceCount <= 0) {
            return 0;
        }
        return Math.min(room, sourceCount);
    }

    public static boolean matchesContainerSize(int containerSlotCount, int blockInventorySize, boolean allowDouble) {
        if (containerSlotCount <= 0 || blockInventorySize <= 0) {
            return false;
        }
        if (containerSlotCount == blockInventorySize) {
            return true;
        }
        return allowDouble && containerSlotCount == blockInventorySize * 2;
    }
}
