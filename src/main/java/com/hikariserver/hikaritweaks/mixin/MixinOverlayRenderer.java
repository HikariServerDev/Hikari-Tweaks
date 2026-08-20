package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.minihud.renderer.OverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// MiniHUD の OverlayRenderer に Redirect を挿し、
// フリーカメラ中でもビーコン範囲をプレイヤー位置基準で描画させる修正。
@Mixin(value = OverlayRenderer.class, remap = false)
public class MixinOverlayRenderer {

    // getCameraEntity() の呼び出しをリダイレクトしてプレイヤーエンティティを返す
    //
    // ★ require = 0（任意適用）にしている理由:
    //   ここは MiniHUD（サードパーティ Mod）の内部実装に依存した注入である。
    //   MiniHUD 未導入ならターゲットクラスごと存在しないので mixin は静かに無効化されるが、
    //   「MiniHUD は入っているがバージョンが違う」場合は話が別で、
    //   renderOverlays の中に EntityUtils.getCameraEntity() の呼び出しが無いビルドだと
    //   mixins.json の injectors.defaultRequire = 1 が効いて InjectionError となり、
    //   **Hikari-Tweaks 全体が起動不能**になる。
    //   renderOverlays のシグネチャは対応 17 ターゲットの間で 7 回変わっており、
    //   MiniHUD 側のバージョン差で注入点が消える可能性は現実的に高い。
    //   「フリーカメラ時のビーコン範囲補正」という単機能のために Mod 全体が
    //   起動できなくなるのは割に合わないので、この注入だけ任意扱いにする。
    //
    //   expect は既定の 1 のまま残す。こうしておくと適用に失敗したときに
    //   Mixin が警告ログを出してくれるので、黙って機能が壊れるのを防げる。
    @Redirect(
        method = "renderOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lfi/dy/masa/malilib/util/EntityUtils;getCameraEntity()Lnet/minecraft/class_1297;"
        ),
        require = 0
    )
    private static Entity hikariTweaks$fixBeaconCamera() {
        // 修正が無効ならデフォルトのカメラエンティティをそのまま返す
        if (!TweaksOptions.FIX_BEACON_RANGE_FREE_CAM.getBooleanValue()) {
            return EntityUtils.getCameraEntity();
        }
        // フリーカメラ中もプレイヤー自身を基準にすることでビーコン範囲が正しく表示される
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player : EntityUtils.getCameraEntity();
    }
}
