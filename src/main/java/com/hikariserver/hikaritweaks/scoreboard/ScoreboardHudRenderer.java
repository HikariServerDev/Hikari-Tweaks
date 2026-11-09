package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.HudCompat;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.v2.ValueInterpolator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;

import java.util.UUID;

public final class ScoreboardHudRenderer {
    private static int currentPage = 0;

    private static final ValueInterpolator INTERPOLATOR = new ValueInterpolator();
    private static int lastBoardGeneration = Integer.MIN_VALUE;

    private ScoreboardHudRenderer() {}

    public static void nextPage() {
        ScoreboardView.Data data = ScoreboardView.current();
        if (data == null) return;
        int max = calcMaxPage(data.size(), ClientConfigManager.config.scoreboardPageSize);
        if (currentPage < max) currentPage++;
    }

    public static void prevPage() {
        if (currentPage > 0) currentPage--;
    }

    public static void resetPage() { currentPage = 0; }

    public static int getCurrentPage() { return currentPage; }

    public static int getMaxPage(int totalEntries, int pageSize) {
        return calcMaxPage(totalEntries, pageSize);
    }

    private static int calcMaxPage(int total, int pageSize) {
        if (pageSize <= 0 || total <= pageSize) return 0;
        return (total - 1) / pageSize;
    }

    private static final String BOT_MARKER = "*";

    private static String rowLabel(ScoreboardView.Data data, int index) {
        String name = data.name(index);
        if (!ClientConfigManager.config.scoreboardMarkBots) return name;
        return data.isBot(index) ? BOT_MARKER + name : name;
    }

    public static void render(DrawCtx ctx) {
        ClientConfig cfg = ClientConfigManager.config;
        if (!cfg.scoreboardCustomHud) return;

        ScoreboardView.Data data = ScoreboardView.current();
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (HudCompat.isHudHidden(mc)) return;
        if (HudCompat.isDebugHudShown(mc)) return;

        Font tr      = mc.font;
        int scaledW          = mc.getWindow().getGuiScaledWidth();
        int scaledH          = mc.getWindow().getGuiScaledHeight();
        float scale          = cfg.scoreboardScale;

        int total = data.size();
        if (total == 0) return;

        int pageSize = Math.max(1, cfg.scoreboardPageSize);
        int maxPage  = calcMaxPage(total, pageSize);
        if (currentPage > maxPage) currentPage = maxPage;

        int startIdx = currentPage * pageSize;
        int endIdx   = Math.min(startIdx + pageSize, total);
        int visible  = endIdx - startIdx;

        int generation = RankingV2Client.generation();
        if (generation != lastBoardGeneration) {
            lastBoardGeneration = generation;
            INTERPOLATOR.reset();
        }

        long nowNanos = System.nanoTime();
        long[] shownValues = new long[visible];
        for (int i = 0; i < visible; i++) {
            int  idx  = startIdx + i;
            long real = data.value(idx);
            UUID key  = data.interpolationKey(idx);
            shownValues[i] = (key != null)
                    ? INTERPOLATOR.display(key, real, nowNanos)
                    : real;
        }
        boolean showTotal  = cfg.scoreboardShowServerTotal && data.serverTotal() >= 0;
        long    realTotal  = data.serverTotal();
        long    shownTotal = (showTotal && data.interpolatable())
                ? INTERPOLATOR.displayTotal(realTotal, nowNanos)
                : realTotal;

        INTERPOLATOR.endFrame();

        String title    = data.title();
        int titleW      = tr.width(title);
        int maxEntryW   = titleW;
        for (int i = 0; i < visible; i++) {
            int idx = startIdx + i;
            String rankStr  = String.format("%2d ", data.rank(idx));
            String nameStr  = rowLabel(data, idx);
            int scoreW = Math.max(
                    tr.width(String.valueOf(data.value(idx))),
                    tr.width(String.valueOf(shownValues[i])));
            int w = tr.width(rankStr) + tr.width(nameStr) + 4 + scoreW;
            if (w > maxEntryW) maxEntryW = w;
        }
        if (maxPage > 0) {
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            int pw = tr.width(pageLine);
            if (pw > maxEntryW) maxEntryW = pw;
        }
        String totalLabel = I18n.get("hikaritweaks.hud.total");
        if (showTotal) {
            int totalValueW = Math.max(
                    tr.width(String.valueOf(realTotal)),
                    tr.width(String.valueOf(shownTotal)));
            int tw = tr.width(totalLabel) + 4 + totalValueW;
            if (tw > maxEntryW) maxEntryW = tw;
        }

        int lineH    = 9;
        int boxW     = maxEntryW + 6;
        int boxH     = (visible + 1) * lineH + 2;
        if (maxPage > 0) boxH += lineH;
        if (showTotal) boxH += lineH;

        int anchorX = (int)(scaledW * cfg.scoreboardPositionX / 100.0);
        int anchorY = (int)(scaledH * cfg.scoreboardPositionY / 100.0);

        ctx.push();
        ctx.translate(anchorX, anchorY, 0);
        ctx.scale(scale, scale, 1.0f);
        ctx.translate(-anchorX / scale, -anchorY / scale, 0);

        int lAnchorX = (int)(anchorX / scale);
        int lAnchorY = (int)(anchorY / scale);

        int xEnd   = lAnchorX - 1;
        int xStart = xEnd - boxW;
        int yStart = lAnchorY - boxH / 2;

        int titleBgY = yStart;
        ctx.fill(xStart, titleBgY, xEnd, titleBgY + lineH + 1,
                cfg.scoreboardHeaderColor);
        int titleX = xStart + (boxW - titleW) / 2;
        ctx.drawTextWithShadow(tr, title, titleX, titleBgY + 1, cfg.scoreboardTextColor);

        int statsRowOffset = 0;
        if (showTotal) {
            int totalRowY = yStart + lineH + 1;
            ctx.fill(xStart, totalRowY, xEnd, totalRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String totalValue = String.valueOf(shownTotal);
            ctx.drawTextWithShadow(tr, totalLabel, xStart + 2, totalRowY, 0xFFAAAAAA);
            int totalValueX = xEnd - tr.width(totalValue) - 2;
            ctx.drawTextWithShadow(tr, totalValue, totalValueX, totalRowY, cfg.scoreboardScoreColor);
            statsRowOffset = lineH;
        }

        for (int i = 0; i < visible; i++) {
            int idx = startIdx + i;
            int globalRank = data.rank(idx);
            int rowY = yStart + (i + 1) * lineH + 1 + statsRowOffset;

            ctx.fill(xStart, rowY, xEnd, rowY + lineH,
                    cfg.scoreboardBodyColor);

            boolean isSelf = data.isSelf(idx);

            String rankStr = String.format("%2d ", globalRank);
            int rankColor  = isSelf ? cfg.scoreboardSelfColor : 0xFFAAAAAA;
            ctx.drawTextWithShadow(tr, rankStr, xStart + 2, rowY, rankColor);

            int rankW   = tr.width(rankStr);
            int nameColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardTextColor;
            ctx.drawTextWithShadow(tr, rowLabel(data, idx), xStart + 2 + rankW, rowY, nameColor);

            String scoreStr = String.valueOf(shownValues[i]);
            int scoreX = xEnd - tr.width(scoreStr) - 2;
            int scoreColor = isSelf ? cfg.scoreboardSelfColor : cfg.scoreboardScoreColor;
            ctx.drawTextWithShadow(tr, scoreStr, scoreX, rowY, scoreColor);
        }

        if (maxPage > 0) {
            int pageRowY = yStart + (visible + 1) * lineH + 1 + statsRowOffset;
            ctx.fill(xStart, pageRowY, xEnd, pageRowY + lineH,
                    cfg.scoreboardHeaderColor);
            String pageLine = "◀ " + (currentPage + 1) + "/" + (maxPage + 1) + " ▶";
            int pageLineX = xStart + (boxW - tr.width(pageLine)) / 2;
            ctx.drawTextWithShadow(tr, pageLine, pageLineX, pageRowY, 0xFFAAAAAA);
        }

        ctx.pop();
    }
}
