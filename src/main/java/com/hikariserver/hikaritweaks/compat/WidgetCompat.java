package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

// GUI ウィジェット生成のバージョン差分を吸収するファサード。
//
// ButtonWidget の生成方法は 2 段階で変わっている:
//   1.19.3  … コンストラクタに NarrationSupplier 引数が追加された
//   1.19.4  … public コンストラクタが廃止され ButtonWidget.builder(...) 方式になった
public final class WidgetCompat {

    private WidgetCompat() {}

    // 指定した矩形にボタンを作る
    public static ButtonWidget button(int x, int y, int width, int height,
                                      Text message, ButtonWidget.PressAction onPress) {
        //? if >=1.19.3 {
        /*return ButtonWidget.builder(message, onPress).dimensions(x, y, width, height).build();
        *///?} else {
        return new ButtonWidget(x, y, width, height, message, onPress);
        //?}
    }

    // ラベルを文字列で受け取るショートハンド
    public static ButtonWidget button(int x, int y, int width, int height,
                                      String label, ButtonWidget.PressAction onPress) {
        return button(x, y, width, height, TextCompat.literal(label), onPress);
    }
}
