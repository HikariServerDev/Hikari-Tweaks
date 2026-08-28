package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.HudCompat;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.List;

// カスタムスコアボード HUD 描画クラス。
//
// ┌─ 配置方針 ────────────────────────────────────────────┐
// │  positionX / positionY は画面幅・高さに対する % (0-100)  │
// │  positionX=100 かつ positionY=50 がデフォルト（右中央）   │
// │  スコアボードの右端が anchorX に、中央が anchorY に来る    │
// └──────────────────────────────────────────────────────┘
public final class ScoreboardHudRenderer {

    // 現在表示中のページ番号（0 始まり）
    private static int currentPage = 0;

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardHudRenderer() {}

    // ── ページ操作 ───────────────────────────────────────

    // 次のページへ移動する（最終ページを超えない）
    public static void nextPage() {
        ScoreboardPacketClient.RankingData data = ScoreboardPacketClient.getCachedRanking();
        if (data == null) return;
        int max = calcMaxPage(data.full().size(), ClientConfigManager.config.scoreboardPageSize);
        if (currentPage < max) currentPage++;
    }

    // 前のページへ移動する（0 未満にならない）
    public static void prevPage() {
        if (currentPage > 0) currentPage--;
    }

    // 先頭ページへリセットする
    public static void resetPage() { currentPage = 0; }

    // 現在のページ番号を返す
    public static int getCurrentPage() { return currentPage; }

    // 総エントリ数とページサイズから最大ページ番号（0 始まり）を返す
    public static int getMaxPage(int totalEntries, int pageSize) {
        return calcMaxPage(totalEntries, pageSize);
    }

    // 最大ページ番号を計算する内部ヘルパー
    private static int calcMaxPage(int total, int pageSize) {
        if (pageSize <= 0 || total <= pageSize) return 0;
        return (total - 1) / pageSize;
    }

    // ── 描画 ─────────────────────────────────────────────

    // インゲーム HUD に描画する。
    // MixinInGameHud から毎フレーム呼ばれる。
    public static void render(DrawCtx ctx) {
        ClientConfig cfg = ClientConfigManager.config;
        // カスタム HUD が無効なら何もしない
        if (!cfg.scoreboardCustomHud) return;
        // サーバーから非表示指示を受けている場合は何もしない
        if (ScoreboardPacketClient.isServerHidden()) return;

        ScoreboardPacketClient.RankingData data = ScoreboardPacketClient.getCachedRanking();
        if (data == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // F1（HUD 非表示）中はカスタムスコアボードも一緒に隠す。
        // MixinInGameHud は InGameHud.render の TAIL に注入しているため、
        // バニラ側の HUD 非表示処理をすり抜けて描画されてしまう。ここで明示的に弾く。
        if (mc.options.hudHidden) return;
        // F3 デバッグ画面表示中はスコアボードを非表示にする
        if (HudCompat.isDebugHudShown(mc)) return;

        TextRenderer tr      = mc.textRenderer;
        int scaledW          = mc.getWindow().getScaledWidth();
        int scaledH          = mc.getWindow().getScaledHeight();
        float scale          = cfg.scoreboardScale;

        // 全エントリリストと総数を取得する
        List<ScoreboardPacketClient.RankingEntry> full = data.full();
        int total = full.size();
        if (total == 0) return;

        int pageSize = Math.max(1, cfg.scoreboardPageSize);
        int maxPage  = calcMaxPage(total, pageSize);
        // ランキング更新でエントリ数が変化してもページが範囲外にならないよう clamp するだけ
        // （resetPage() は呼ばず、可能な限り現在ページを維持する）
        if (currentPage > maxPage) currentPage = maxPage;

        // 表示するエントリ範囲を計算する
        int startIdx = currentPage * pageSize;
        int endIdx   = Math.min(startIdx + pageSize, total);
        List<ScoreboardPacketClient.RankingEntry> display = full.subList(startIdx, endIdx);

        // ── 幅の計算（スケール前の論理ピクセルで）──────────────
        String title    = data.title();
        int titleW      = tr.getWidth(title);
        int maxEntryW   = titleW;
        // 各エントリ行の最大幅を求める
        for (int i = 0; i < display.size(); i++) {
            int globalRank = startIdx + i + 1;
            String rankStr  = String.format("%2d ", globalRank);
            String nameStr  = display.get(i).name();
            String scoreStr = String.valueOf(display.get(i).value());
            int w = tr.getWidth(rankStr) + tr.getWidth(nameStr) + 4 + tr.getWidth(scoreStr);
            if (w > maxEntryW) maxEntryW = w;
        }
        // ページ表示行の幅を考慮する
        if (maxPage > 0) {
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            int pw = tr.getWidth(pageLine);
            if (pw > maxEntryW) maxEntryW = pw;
        }
        // サーバートータル行の幅を考慮する
        if (cfg.scoreboardShowServerTotal && data.serverTotal() >= 0) {
            String totalLabel = "Total:";
            String totalValue = String.valueOf(data.serverTotal());
            int tw = tr.getWidth(totalLabel) + 4 + tr.getWidth(totalValue);
            if (tw > maxEntryW) maxEntryW = tw;
        }

        // ボックスサイズを計算する（左右3pxずつパディング）
        int lineH    = 9;
        int boxW     = maxEntryW + 6;   // 左右3pxずつパディング
        int boxH     = (display.size() + 1) * lineH + 2; // +1 = タイトル行, +2 = 上下1px
        if (maxPage > 0) boxH += lineH; // ページ行
        if (cfg.scoreboardShowServerTotal) boxH += lineH; // サーバートータル行

        // ── アンカー座標（スケール後ピクセル）─────────────────
        int anchorX = (int)(scaledW * cfg.scoreboardPositionX / 100.0);
        int anchorY = (int)(scaledH * cfg.scoreboardPositionY / 100.0);

        // スケール変換をマトリクススタックに積む
        ctx.push();
        ctx.translate(anchorX, anchorY, 0);
        ctx.scale(scale, scale, 1.0f);
        ctx.translate(-anchorX / scale, -anchorY / scale, 0);

        // スケール後の論理座標でアンカーを再計算する
        int lAnchorX = (int)(anchorX / scale);
        int lAnchorY = (int)(anchorY / scale);

        // スコアボードの右端 = lAnchorX、縦中央 = lAnchorY
        int xEnd   = lAnchorX - 1;
        int xStart = xEnd - boxW;
        int yStart = lAnchorY - boxH / 2;

        // ── タイトル行 ──────────────────────────────────────
        int titleBgY = yStart;
        // ヘッダー背景を描画する
        ctx.fill(xStart, titleBgY, xEnd, titleBgY + lineH + 1,
                cfg.scoreboardHeaderColor);
        // タイトルテキストを中央揃えで描画する
        int titleX = xStart + (boxW - titleW) / 2;
        ctx.drawTextWithShadow(tr, title, titleX, titleBgY + 1, cfg.scoreboardTextColor);

        // ── サーバートータル行（タイトルの直下、エントリの上）──
        int statsRowOffset = 0;
        if (cfg.scoreboardShowServerTotal && data.serverTotal() >= 0) {
            int totalRowY = yStart + lineH + 1;
            ctx.fill(xStart, totalRowY, xEnd, totalRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String totalLabel = "Total:";
            String totalValue = String.valueOf(data.serverTotal());
            // ラベルを左寄せ、数値を右寄せで描画する
            ctx.drawTextWithShadow(tr, totalLabel, xStart + 2, totalRowY, 0xFFAAAAAA);
            int totalValueX = xEnd - tr.getWidth(totalValue) - 2;
            ctx.drawTextWithShadow(tr, totalValue, totalValueX, totalRowY, cfg.scoreboardScoreColor);
            statsRowOffset = lineH;
        }

        // ── エントリ行 ──────────────────────────────────────
        for (int i = 0; i < display.size(); i++) {
            int globalRank = startIdx + i + 1;
            ScoreboardPacketClient.RankingEntry entry = display.get(i);
            int rowY = yStart + (i + 1) * lineH + 1 + statsRowOffset;

            // エントリ行の背景を描画する
            ctx.fill(xStart, rowY, xEnd, rowY + lineH,
                    cfg.scoreboardBodyColor);

            // 自分かどうかを判定して色を変える
            boolean isSelf = entry.name().equals(data.selfName());

            // 順位を描画する（自分は強調色）
            String rankStr = String.format("%2d ", globalRank);
            int rankColor  = isSelf ? cfg.scoreboardSelfColor : 0xFFAAAAAA;
            ctx.drawTextWithShadow(tr, rankStr, xStart + 2, rowY, rankColor);

            // プレイヤー名を描画する（自分は強調色）
            int rankW   = tr.getWidth(rankStr);
            int nameColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardTextColor;
            ctx.drawTextWithShadow(tr, entry.name(), xStart + 2 + rankW, rowY, nameColor);

            // スコア数値を右寄せで描画する（自分は強調色）
            String scoreStr = String.valueOf(entry.value());
            int scoreX = xEnd - tr.getWidth(scoreStr) - 2;
            int scoreColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardScoreColor;
            ctx.drawTextWithShadow(tr, scoreStr, scoreX, rowY, scoreColor);
        }

        // ── ページ行 ────────────────────────────────────────
        // ページが複数ある場合のみページナビゲーション行を描画する
        if (maxPage > 0) {
            int pageRowY = yStart + (display.size() + 1) * lineH + 1 + statsRowOffset;
            ctx.fill(xStart, pageRowY, xEnd, pageRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            // ページ表示を中央揃えで描画する
            int pageLineX = xStart + (boxW - tr.getWidth(pageLine)) / 2;
            ctx.drawTextWithShadow(tr, pageLine, pageLineX, pageRowY, 0xFFAAAAAA);
        }

        // マトリクスを元に戻す
        ctx.pop();
    }
}
