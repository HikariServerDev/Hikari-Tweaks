package com.hikariserver.hikaritweaks.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPickerRulesTest {
    private static final String ROCKET_ID = "minecraft:firework_rocket";

    @Test
    @DisplayName("空の検索語はすべてに当てはまる")
    void emptyQueryMatchesEverything() {
        assertTrue(ItemPickerRules.matches("", ROCKET_ID, "ロケット花火"));
        assertTrue(ItemPickerRules.matches("   ", ROCKET_ID, "ロケット花火"));
        assertTrue(ItemPickerRules.matches(null, ROCKET_ID, "ロケット花火"));
    }

    @Test
    @DisplayName("表示名（いまの言語）で引ける")
    void matchesDisplayName() {
        assertTrue(ItemPickerRules.matches("花火", ROCKET_ID, "ロケット花火"));
        assertFalse(ItemPickerRules.matches("にんじん", ROCKET_ID, "ロケット花火"));
    }

    @Test
    @DisplayName("ID で引ける（名前空間の有無どちらでも）")
    void matchesId() {
        assertTrue(ItemPickerRules.matches("firework_rocket", ROCKET_ID, "ロケット花火"));
        assertTrue(ItemPickerRules.matches(ROCKET_ID, ROCKET_ID, "ロケット花火"));
        assertTrue(ItemPickerRules.matches("rocket", ROCKET_ID, "ロケット花火"));
    }

    @Test
    @DisplayName("空白を _ に読み替えるので、日本語設定でも英語名で引ける")
    void spacesMatchUnderscoresInId() {
        assertTrue(ItemPickerRules.matches("firework rocket", ROCKET_ID, "ロケット花火"));
        assertTrue(ItemPickerRules.matches("Golden Carrot", "minecraft:golden_carrot", "金のニンジン"));
    }

    @Test
    @DisplayName("大文字小文字を区別しない")
    void ignoresCase() {
        assertTrue(ItemPickerRules.matches("FIREWORK", ROCKET_ID, "Firework Rocket"));
        assertTrue(ItemPickerRules.matches("firework", ROCKET_ID, "Firework Rocket"));
    }

    @Test
    @DisplayName("リストに無ければ末尾へ足す")
    void toggleAddsToTheEnd() {
        assertEquals(List.of("minecraft:golden_carrot", ROCKET_ID),
                ItemPickerRules.toggle(List.of("minecraft:golden_carrot"), ROCKET_ID));
    }

    @Test
    @DisplayName("リストにあれば取り除く（手で書いた重複も全部）")
    void toggleRemovesEveryOccurrence() {
        assertEquals(List.of("minecraft:golden_carrot"),
                ItemPickerRules.toggle(List.of(ROCKET_ID, "minecraft:golden_carrot", ROCKET_ID), ROCKET_ID));
    }

    @Test
    @DisplayName("元のリストは変更しない")
    void toggleDoesNotMutateInput() {
        List<String> original = List.of(ROCKET_ID);
        assertEquals(List.of(), ItemPickerRules.toggle(original, ROCKET_ID));
        assertEquals(List.of(ROCKET_ID), original);
    }

    @Test
    @DisplayName("列数は幅を 18px で割った数（最低 1）")
    void columnsFitTheWidth() {
        assertEquals(20, ItemPickerRules.columns(360));
        assertEquals(20, ItemPickerRules.columns(377));
        assertEquals(1, ItemPickerRules.columns(5));
    }

    @Test
    @DisplayName("行数は切り上げ")
    void rowCountRoundsUp() {
        assertEquals(0, ItemPickerRules.rowCount(0, 9));
        assertEquals(1, ItemPickerRules.rowCount(9, 9));
        assertEquals(2, ItemPickerRules.rowCount(10, 9));
    }

    @Test
    @DisplayName("マスの位置は左上から 18px 刻み。範囲外は -1")
    void cellHitTest() {
        assertEquals(0, ItemPickerRules.rowAt(56, 56, 7));
        assertEquals(0, ItemPickerRules.rowAt(73.9, 56, 7));
        assertEquals(1, ItemPickerRules.rowAt(74, 56, 7));
        assertEquals(-1, ItemPickerRules.rowAt(55, 56, 7));
        assertEquals(-1, ItemPickerRules.rowAt(56 + 7 * 18, 56, 7));
        assertEquals(0, ItemPickerRules.columnAt(10, 10, 20));
        assertEquals(19, ItemPickerRules.columnAt(10 + 19 * 18 + 17, 10, 20));
        assertEquals(-1, ItemPickerRules.columnAt(9, 10, 20));
        assertEquals(-1, ItemPickerRules.columnAt(10 + 20 * 18, 10, 20));
    }
}
