package com.hikariserver.hikaritweaks.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.MinecraftClient;

// Litematica のマテリアルリスト自動 Refresh ハンドラ。
//
// MixinSchematicWorldRefresher がレイヤー変更（updateBetweenX/Y/Z）を検知したとき
// scheduleRefresh() を呼び出す。次の tick で reCreateMaterialList() を実行することで
// レイヤー変更直後の正しい状態でリストが更新される。
//
// Litematica がない環境では MixinSchematicWorldRefresher 自体がロードされないため
// scheduleRefresh() が呼ばれることはなく、tick() は何もしない。
public final class LitematicaAutoRefreshHandler {

    // Mixin から通知された Refresh 予約フラグ
    private static boolean refreshScheduled = false;

    // インスタンス化を禁止するプライベートコンストラクタ
    private LitematicaAutoRefreshHandler() {}

    // MixinSchematicWorldRefresher から呼ばれる。次 tick で Refresh を実行するよう予約する。
    public static void scheduleRefresh() {
        refreshScheduled = true;
    }

    // ClientTickEvents.END_CLIENT_TICK から毎 tick 呼ばれる。
    // 予約フラグが立っていれば DataManager からマテリアルリストを取得して再生成する。
    public static void tick(MinecraftClient client) {
        // 予約がなければ何もしない
        if (!refreshScheduled) return;
        // フラグを消費してから処理する（二重実行を防ぐ）
        refreshScheduled = false;

        // ワールドまたはプレイヤーが存在しない場合はリフレッシュできない
        if (client.world == null || client.player == null) return;

        // Litematica の DataManager からマテリアルリストを取得して再生成する
        MaterialListBase materialList = DataManager.getMaterialList();
        if (materialList != null) {
            materialList.reCreateMaterialList();
        }
    }

    // ワールド切り替え時にフラグをリセットする
    public static void reset() {
        refreshScheduled = false;
    }
}
