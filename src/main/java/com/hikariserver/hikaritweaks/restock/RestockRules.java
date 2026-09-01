package com.hikariserver.hikaritweaks.restock;

// restock パッケージの「Minecraft の型に触らない判断ロジック」だけを集めたクラス。
// ハンドラ本体はクライアントが無いと動かせないので、
// 数え方・条件の判定だけをここに切り出してユニットテストで検証する。
public final class RestockRules {

    // インスタンス化を禁止するプライベートコンストラクタ
    private RestockRules() {}

    // 1 回の補充で実際に動かせる個数を返す。
    // 0 を返したときは「1 個も動かせない ＝ 補充は no-op」という意味で、
    // 呼び出し側は「補充した」と扱ってはいけない（リトライ用のタイマーを
    // リセットすると、同じ no-op を一定間隔で永遠に繰り返すことになる）。
    public static int plannedMoveCount(int hotbarCount, int maxCount, int sourceCount) {
        int room = maxCount - hotbarCount;
        if (room <= 0 || sourceCount <= 0) {
            return 0;
        }
        return Math.min(room, sourceCount);
    }

    // 開いている画面のコンテナスロット数が、クロスヘアの先にあるブロックエンティティの
    // 中身のサイズとして辻褄が合うかどうかを返す。
    // allowDouble は隣接してサイズが 2 倍になりうるチェストのときだけ true にする。
    //
    // これはプラグインの仮想 GUI を弾くための補助的な条件。
    // 「単チェストを右クリックしたら 54 スロットのショップ GUI が開いた」のような
    // 食い違いを検出できるが、たまたまサイズが一致する仮想 GUI は検出できない。
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
