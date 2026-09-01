package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
//? if >=1.20 {
/*import net.minecraft.client.gui.DrawContext;
*///?} else {
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
//?}

// 2D 描画 API のバージョン差分を吸収するラッパー。
//
// 描画コードはこのクラスだけを介して描画し、
// 生の MatrixStack / DrawContext には触らないようにする。
//
// 変遷:
//   〜1.19.4 : MatrixStack + DrawableHelper の静的メソッド
//   1.20〜   : DrawContext のインスタンスメソッド。行列は MatrixStack（3D）
//   1.21.6〜 : DrawContext.getMatrices() が JOML の Matrix3x2fStack（2D）になり、
//              push/pop が pushMatrix/popMatrix に、translate/scale が 2 引数になった。
//              あわせて TextRenderer のアルファ補完（tweakTransparency）が消え、
//              アルファ 0 のテキスト色は描画されずに捨てられるようになった
//              → drawTextWithShadow で ColorCompat.opaqueIfNoAlpha() を通して吸収する
public final class DrawCtx {

    //? if >=1.20 {
    /*private final DrawContext ctx;

    public DrawCtx(DrawContext ctx) {
        this.ctx = ctx;
    }

    // 生の DrawContext を返す（malilib へ渡すときなど）
    public DrawContext raw() {
        return ctx;
    }
    *///?} else {
    private final MatrixStack matrices;

    public DrawCtx(MatrixStack matrices) {
        this.matrices = matrices;
    }

    // 生の MatrixStack を返す（malilib へ渡すときなど）
    public MatrixStack raw() {
        return matrices;
    }
    //?}

    // ── 矩形塗りつぶし ───────────────────────────────────────

    // (x1,y1)-(x2,y2) の矩形を ARGB 色で塗る
    public void fill(int x1, int y1, int x2, int y2, int color) {
        //? if >=1.20 {
        /*ctx.fill(x1, y1, x2, y2, color);
        *///?} else {
        DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
        //?}
    }

    // ── テキスト描画 ─────────────────────────────────────────

    // 影付きテキストを描画する。
    // 1.20 以降は DrawContext が int 座標しか受け付けないため、
    // 小数座標は切り捨てられる点に注意。
    //
    // ★ ColorCompat.opaqueIfNoAlpha() を外してはならない。
    //   1.21.6 の GUI レンダラ刷新で TextRenderer の tweakTransparency（アルファ補完）が
    //   削除され、DrawContext#drawText がアルファ 0 の色で即 return するようになった。
    //   0xFFFFFF のようなアルファ無しの色を渡している呼び出し元が全滅する
    //   （1.21.11 実機で「文字だけ描かれない」不具合として発覚）。
    //   1.21.5 以前ではバニラ側が同じ変換を掛け直すだけなので挙動は変わらない。
    //   詳細な経緯は ColorCompat のコメントを参照。
    public void drawTextWithShadow(TextRenderer tr, String text, float x, float y, int color) {
        int c = ColorCompat.opaqueIfNoAlpha(color);
        //? if >=1.20 {
        /*ctx.drawTextWithShadow(tr, text, (int) x, (int) y, c);
        *///?} else {
        tr.drawWithShadow(matrices, text, x, y, c);
        //?}
    }

    // ── 行列操作 ─────────────────────────────────────────────
    //
    // 1.21.6 以降は 2D 行列スタックなので z 成分は捨てる。
    // このクラスの用途（HUD のスケーリング）では z は常に 0 か 1 なので影響しない。

    public void push() {
        //? if >=1.21.6 {
        /*ctx.getMatrices().pushMatrix();
        *///?} elif >=1.20 {
        /*ctx.getMatrices().push();
        *///?} else {
        matrices.push();
        //?}
    }

    public void pop() {
        //? if >=1.21.6 {
        /*ctx.getMatrices().popMatrix();
        *///?} elif >=1.20 {
        /*ctx.getMatrices().pop();
        *///?} else {
        matrices.pop();
        //?}
    }

    public void translate(float x, float y, float z) {
        //? if >=1.21.6 {
        /*ctx.getMatrices().translate(x, y);
        *///?} elif >=1.20 {
        /*ctx.getMatrices().translate(x, y, z);
        *///?} else {
        matrices.translate(x, y, z);
        //?}
    }

    public void scale(float x, float y, float z) {
        //? if >=1.21.6 {
        /*ctx.getMatrices().scale(x, y);
        *///?} elif >=1.20 {
        /*ctx.getMatrices().scale(x, y, z);
        *///?} else {
        matrices.scale(x, y, z);
        //?}
    }

    // ── シザー（描画範囲のクリップ）─────────────────────────

    // 画面座標 (left, top)-(right, bottom) で描画範囲を制限する
    public void enableScissor(int left, int top, int right, int bottom) {
        //? if >=1.20 {
        /*ctx.enableScissor(left, top, right, bottom);
        *///?} else {
        // 1.20 未満は GL のシザー矩形（原点が左下・フレームバッファ座標）を直接指定する。
        //
        // ★ 高さは必ず Window.getFramebufferHeight() を使うこと。getHeight() ではない。
        //   glScissor が受け取るのは**実ピクセル（フレームバッファ座標）**であり、
        //   Window.height（ウィンドウ座標）とは別フィールド。
        //   Windows / 等倍 DPI では両者が一致するので開発機では絶対に再現しないが、
        //   macOS Retina のような HiDPI 環境では framebufferHeight が height の 2 倍になり、
        //   getHeight() を使うと矩形の Y 原点が画面外へ飛んでプレイヤー一覧が 1 行も描かれない。
        //   バニラの DrawableHelper.setScissor も getFramebufferHeight() を使っている
        //   （javap で確認済み。幅・高さは Math.max(0, ...) でクランプしている）。
        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();
        double scale = window.getScaleFactor();
        int fbHeight = window.getFramebufferHeight();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                (int) (left * scale),
                (int) (fbHeight - bottom * scale),
                Math.max(0, (int) ((right - left) * scale)),
                Math.max(0, (int) ((bottom - top) * scale)));
        //?}
    }

    public void disableScissor() {
        //? if >=1.20 {
        /*ctx.disableScissor();
        *///?} else {
        // enableScissor 側がバニラの ScissorStack を使わず glScissor を直接設定しているため、
        // ここも対応する形で直接無効化する（座標を扱わないので高さ問題の影響は無い）。
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        //?}
    }
}
