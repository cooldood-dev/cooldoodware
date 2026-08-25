package com.github.cooldood.modules.impl.render;

import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.ClientTickEvent;
import com.github.cooldood.events.impl.MotionEvent;
import com.github.cooldood.events.impl.PacketEvent;
import com.github.cooldood.modules.*;
import com.github.cooldood.modules.impl.client.ThemeModule;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.minecraft.TimerUtil;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.RenderUtil;
import com.github.cooldood.utils.render.draggable.Draggable;
import com.github.cooldood.utils.tenacity.render.RoundedUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RegisterModule(
        name = "Statistics",
        description = "Provides Statistics functionality for the client.",
        category = Category.RENDER
)
public class Statistics extends Module {
    // ── Existing data fields — DO NOT MODIFY ──────────────────────────────
    public static int gamesPlayed, killCount, deathCount, victoryCount;
    public static long startTime = System.currentTimeMillis(), endTime = -1;
    private static long lastVictoryTime = 0;
    public static final String[] KILL_TRIGGERS = {"by *", "para *", "fue destrozado a manos de *"};
    private static final Pattern WIN_MESSAGE_PATTERN = Pattern.compile(
            "(?i).*\\b(1st place!?|#1!?|victory!?|winner!?|won the game|you won(?: the game)?|team wins!?)\\b.*"
    );

    @RegisterSubModule(name = "Show Speed Graph")
    public static boolean motionGraph = true;
    @RegisterSubModule(name = "Separate Graph")
    public static boolean seprateMotionGraph = true;
    @RegisterSubModule(name = "Scale", min = 0.5, max = 2.0, increment = 0.05)
    public static float scale = 1.0f;

    private static final Map<String, Double> statistics = new LinkedHashMap<>();
    private static final List<Float> speeds = new ArrayList<>();

    // ── Reference-image color palette ─────────────────────────────────────
    // Outer panel: very dark charcoal, nearly opaque
    private static final Color BG_OUTER    = new Color(22, 22, 28, 240);
    // Inner cards: slightly lighter charcoal
    private static final Color BG_INNER    = new Color(32, 32, 40, 255);
    // Icon square bg inside inner cards
    private static final Color ICON_BG     = new Color(48, 36, 64, 255);
    // Outer border: dim purple glow tint
    private static final Color BORDER_OUT  = new Color(140, 80, 220, 70);
    // Inner card border: subtle highlight
    private static final Color BORDER_IN   = new Color(255, 255, 255, 18);
    // Separator line between rows
    private static final Color SEPARATOR   = new Color(255, 255, 255, 14);
    // Primary white text
    private static final Color TEXT_WHITE  = new Color(230, 230, 240, 255);
    // Muted gray label text
    private static final Color TEXT_MUTED  = new Color(160, 160, 175, 255);

    // Layout constants — tuned to match reference proportions
    private static final float PANEL_W     = 210f;   // total panel width
    private static final float OUTER_RAD   = 14f;    // outer corner radius
    private static final float INNER_RAD   = 10f;    // inner card corner radius
    private static final float ICON_RAD    = 8f;     // icon square corner radius
    private static final float ICON_SZ     = 24f;    // icon square side length
    private static final float PAD_OUT     = 11f;    // outer container padding
    private static final float PAD_IN      = 8f;     // inner card padding
    private static final float ROW_H       = 34f;    // height of each stat row (reduced)
    private static final float HEADER_H    = 38f;    // header section height (reduced)
    private static final float PT_ROW_H    = 32f;    // play-time row height (reduced)
    private static final float BAR_H       = 5f;     // progress bar height
    private static final float GAP         = 7f;     // gap between sections (reduced)

    // Font sizes — labels & values increased for readability
    private static final int SZ_TITLE  = 14;   // "SESSION STATS" label
    private static final int SZ_LABEL  = 12;   // stat row label ("Games Played") — was 10
    private static final int SZ_VALUE  = 14;   // stat row value ("5") — was 12
    private static final int SZ_PT_LBL = 11;   // "Play Time" label — was 10
    private static final int SZ_PT_VAL = 20;   // "05:29" bold value — was 18
    private static final int SZ_ICON   = 9;    // icon glyph size

    private static float width, height;

    // ── Draggables (same IDs as before — preserves saved positions) ───────
    public static Draggable dragging = new Draggable(
            "sessionstats",
            Statistics::renderStatistics,
            e -> ModuleManager.isEnabled(Statistics.class),
            e -> true
    );

    public static Draggable motionDragging = new Draggable(
            "motionGraph",
            Statistics::renderMotionGraph,
            e -> ModuleManager.isEnabled(Statistics.class) && motionGraph && seprateMotionGraph,
            e -> true
    );

    // ── Accent color from theme ────────────────────────────────────────────
    private static Color accent() {
        // Use theme primary color but force into purple family if theme is default
        Color[] theme = ThemeModule.getThemeColours();
        return theme[0];
    }

    // ── Icon drawing via small GL rounded-rect dots (font-atlas independent)
    // Each icon is represented by a small purple rounded square.
    // Additional geometric details are drawn inside using GL primitives.
    private static void drawIconSquare(float x, float y) {
        RenderUtil.drawRoundedRect(x, y, ICON_SZ, ICON_SZ, ICON_RAD, ICON_BG);
    }

    /**
     * Draw a simple icon glyph inside the icon square using font chars.
     * Centered both horizontally and vertically.
     */
    private static void drawIconGlyph(float ix, float iy, String glyph, Color color) {
        float gw = FontUtil.getStringWidth(glyph, SZ_ICON);
        float gh = FontUtil.getFontHeight(SZ_ICON);
        float gx = ix + (ICON_SZ - gw) / 2f;
        float gy = iy + (ICON_SZ - gh) / 2f;
        FontUtil.drawString(glyph, gx, gy, SZ_ICON, color, false);
    }

    // ── Draw horizontal separator line ────────────────────────────────────
    private static void drawSep(float x, float y, float w) {
        RenderUtil.drawRect(x, y, w, 0.7f, SEPARATOR);
    }

    // ── Main render ───────────────────────────────────────────────────────
    private static double[] renderStatistics() {
        updateSize();

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);

        Color ac = accent();
        // Translucent purple for accent text
        Color accentText = new Color(
                Math.min(255, (int)(ac.getRed()   * 1.1f)),
                (int)(ac.getGreen() * 0.7f),
                Math.min(255, (int)(ac.getBlue()  * 1.1f)),
                255);

        float contentW = PANEL_W - 2 * PAD_OUT;

        // ── Compute heights ───────────────────────────────────────────────
        int statCount = statistics.isEmpty() ? 4 : statistics.size();
        float statsCardH  = PAD_IN + statCount * ROW_H + PAD_IN;
        float ptCardH     = PAD_IN + PT_ROW_H + 8 + BAR_H + PAD_IN;

        height = PAD_OUT + HEADER_H + GAP + statsCardH + GAP + ptCardH + PAD_OUT;
        width  = PANEL_W;

        // ═══════════════════════════════════════════════════════════════════
        // 1. OUTER PANEL — dark glass with purple edge border
        // ═══════════════════════════════════════════════════════════════════
        // Shadow layer (offset slightly, very transparent)
        RenderUtil.drawRoundedRect(-3, 3, width + 6, height + 2, OUTER_RAD + 1, new Color(0, 0, 0, 60));

        // Main background
        RenderUtil.drawRoundedRect(0, 0, width, height, OUTER_RAD, BG_OUTER);

        // Purple edge glow/border
        RenderUtil.drawRoundedRectOutline(0, 0, width, height, OUTER_RAD, 1.0f,
                new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 90));

        // Subtle inner highlight (top-left rim)
        RenderUtil.drawRoundedRectOutline(1, 1, width - 2, height - 2, OUTER_RAD - 1, 0.6f,
                new Color(255, 255, 255, 16));

        // ═══════════════════════════════════════════════════════════════════
        // 2. HEADER
        // ═══════════════════════════════════════════════════════════════════
        float hx = PAD_OUT;
        float hy = PAD_OUT;

        // Icon square (top-left of header)
        drawIconSquare(hx, hy);
        // Chart icon — "M" shaped line chart using ASCII that renders reliably
        FontUtil.drawString("~", hx + (ICON_SZ - FontUtil.getStringWidth("~", SZ_ICON)) / 2f,
                hy + (ICON_SZ - FontUtil.getFontHeight(SZ_ICON)) / 2f,
                SZ_ICON, accentText, false);

        // "SESSION" in white, "STATS" in accent purple
        float titleX = hx + ICON_SZ + 10;
        float titleY = hy + (ICON_SZ - FontUtil.getFontHeight(SZ_TITLE)) / 2f;

        String sessionStr = "SESSION ";
        FontUtil.drawString(sessionStr, titleX, titleY, SZ_TITLE, TEXT_WHITE, false);
        float sessionW = FontUtil.getStringWidth(sessionStr, SZ_TITLE);
        FontUtil.drawString("STATS", titleX + sessionW, titleY, SZ_TITLE, accentText, false);

        // Status dot — far right of header, vertically centered
        float dotCX = width - PAD_OUT - 5;
        float dotCY = hy + ICON_SZ / 2f;
        RenderUtil.drawRoundedRect(dotCX - 4, dotCY - 4, 8, 8, 4f, accentText);

        // ═══════════════════════════════════════════════════════════════════
        // 3. STATS CARD
        // ═══════════════════════════════════════════════════════════════════
        float cardX = PAD_OUT;
        float cardY = hy + ICON_SZ + GAP;

        // Inner card background
        RenderUtil.drawRoundedRect(cardX, cardY, contentW, statsCardH, INNER_RAD, BG_INNER);
        // Inner card border
        RenderUtil.drawRoundedRectOutline(cardX, cardY, contentW, statsCardH, INNER_RAD, 0.7f, BORDER_IN);

        // Stat rows
        // Map icon glyphs: use ASCII that renders in DM Sans Bold
        String[] rowIcons   = { "G", "V", "+", "K" };
        String[] rowKeys    = { "Games Played", "Victories", "K/D", "Kills" };
        boolean[] useAccent = { false, false, true, false };

        float rowY = cardY + PAD_IN;
        int ri = 0;
        for (Map.Entry<String, Double> entry : statistics.entrySet()) {
            String label = entry.getKey();
            boolean isKD = label.equals("K/D");
            String value = isKD
                    ? String.valueOf(entry.getValue().doubleValue())
                    : String.valueOf(entry.getValue().intValue());

            // Row vertical center
            float rowCY = rowY + ROW_H / 2f;
            float iconY = rowCY - ICON_SZ / 2f;

            // Icon square
            float iconX = cardX + PAD_IN;
            drawIconSquare(iconX, iconY);
            // Icon glyph
            String glyph = ri < rowIcons.length ? rowIcons[ri] : "?";
            FontUtil.drawString(glyph,
                    iconX + (ICON_SZ - FontUtil.getStringWidth(glyph, SZ_ICON)) / 2f,
                    iconY + (ICON_SZ - FontUtil.getFontHeight(SZ_ICON)) / 2f,
                    SZ_ICON, accentText, false);

            // Label (muted gray)
            float labelX = iconX + ICON_SZ + 10;
            float labelY = rowCY - FontUtil.getFontHeight(SZ_LABEL) / 2f;
            FontUtil.drawString(label, labelX, labelY, SZ_LABEL, TEXT_MUTED, false);

            // Value (right-aligned, accent if KD else white)
            Color valColor = isKD ? accentText : TEXT_WHITE;
            float valW = FontUtil.getStringWidth(value, SZ_VALUE);
            float valX = cardX + contentW - PAD_IN - valW;
            float valY = rowCY - FontUtil.getFontHeight(SZ_VALUE) / 2f;
            FontUtil.drawString(value, valX, valY, SZ_VALUE, valColor, false);

            rowY += ROW_H;

            // Separator (not after last row)
            if (ri < statCount - 1) {
                float sepX = cardX + PAD_IN;
                float sepW = contentW - 2 * PAD_IN;
                drawSep(sepX, rowY - 0.5f, sepW);
            }

            ri++;
        }

        // ═══════════════════════════════════════════════════════════════════
        // 4. PLAY TIME CARD
        // ═══════════════════════════════════════════════════════════════════
        float ptCardY = cardY + statsCardH + GAP;

        // Card background + border
        RenderUtil.drawRoundedRect(cardX, ptCardY, contentW, ptCardH, INNER_RAD, BG_INNER);
        RenderUtil.drawRoundedRectOutline(cardX, ptCardY, contentW, ptCardH, INNER_RAD, 0.7f, BORDER_IN);

        // Row: icon + "Play Time" label + "MM:SS" value
        float ptRowCY = ptCardY + PAD_IN + PT_ROW_H / 2f;
        float ptIconY = ptRowCY - ICON_SZ / 2f;

        // Clock icon square
        drawIconSquare(cardX + PAD_IN, ptIconY);
        FontUtil.drawString("O",
                cardX + PAD_IN + (ICON_SZ - FontUtil.getStringWidth("O", SZ_ICON)) / 2f,
                ptIconY + (ICON_SZ - FontUtil.getFontHeight(SZ_ICON)) / 2f,
                SZ_ICON, accentText, false);

        // "Play Time" label
        float ptLabelX = cardX + PAD_IN + ICON_SZ + 10;
        float ptLabelY = ptRowCY - FontUtil.getFontHeight(SZ_PT_LBL) / 2f;
        FontUtil.drawString("Play Time", ptLabelX, ptLabelY, SZ_PT_LBL, TEXT_MUTED, false);

        // Play time value — large, right-aligned, accent purple
        int[] playTime = getPlayTime();
        String playTimeStr = formatPlayTime(playTime);
        float ptValW = FontUtil.getStringWidth(playTimeStr, SZ_PT_VAL);
        float ptValX = cardX + contentW - PAD_IN - ptValW;
        float ptValY = ptRowCY - FontUtil.getFontHeight(SZ_PT_VAL) / 2f;
        FontUtil.drawString(playTimeStr, ptValX, ptValY, SZ_PT_VAL, accentText, false);

        // ── Progress Bar ─────────────────────────────────────────────────
        float barY   = ptCardY + PAD_IN + PT_ROW_H + 4;
        float barX   = cardX + PAD_IN;
        float barW   = contentW - 2 * PAD_IN;

        // Track
        RenderUtil.drawRoundedRect(barX, barY, barW, BAR_H, BAR_H / 2f, new Color(15, 15, 20, 200));

        // Fill with accent gradient — capped at 1 hour = 60 min
        float pct = Math.min((playTime[1] + playTime[2] / 60f) / 60f, 1f);
        if (pct > 0.01f) {
            Color[] grad = RenderUtil.getColorsFade(0, barW, ThemeModule.getThemeColours(), 4f);
            RenderUtil.drawGradientLR(barX, barY, barW * pct, BAR_H, grad[0], grad[1]);
            // Glow effect: slightly wider, very transparent
            RenderUtil.drawRoundedRect(barX, barY - 1, barW * pct, BAR_H + 2, BAR_H / 2f,
                    new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 35));
        }

        // ── Speed Graph (integrated, if enabled) ─────────────────────────
        if (motionGraph && !seprateMotionGraph) {
            float graphCardY = ptCardY + ptCardH + GAP;
            float graphInnerH = FontUtil.getFontHeight(SZ_LABEL) + 8 + 40;
            float graphCardH  = PAD_IN * 2 + graphInnerH;

            // Extend rendered height
            height = graphCardY + graphCardH + PAD_OUT;

            RenderUtil.drawRoundedRect(cardX, graphCardY, contentW, graphCardH, INNER_RAD, BG_INNER);
            RenderUtil.drawRoundedRectOutline(cardX, graphCardY, contentW, graphCardH, INNER_RAD, 0.7f, BORDER_IN);

            float gCursor = graphCardY + PAD_IN;
            FontUtil.drawString("Speed (BPS)", cardX + PAD_IN, gCursor, SZ_LABEL, TEXT_MUTED, false);
            String avgText = getAverageSpeed() + " avg";
            FontUtil.drawString(avgText,
                    cardX + contentW - PAD_IN - FontUtil.getStringWidth(avgText, SZ_LABEL),
                    gCursor, SZ_LABEL, accentText, false);
            gCursor += FontUtil.getFontHeight(SZ_LABEL) + 8;
            drawSpeedPlot(cardX + PAD_IN, gCursor, contentW - 2 * PAD_IN, 40, ac);
        }

        GL11.glPopMatrix();
        return new double[]{width * scale, height * scale};
    }

    // ── Motion graph (separate draggable) ─────────────────────────────────
    private static double[] renderMotionGraph() {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);

        Color ac = accent();
        float contentW = PANEL_W - 2 * PAD_OUT;
        float innerH   = FontUtil.getFontHeight(SZ_LABEL) + 8 + 50;
        float graphCardH = PAD_IN * 2 + innerH;
        float panelH     = PAD_OUT * 2 + graphCardH;

        width  = PANEL_W;
        height = panelH;

        RenderUtil.drawRoundedRect(0, 0, width, height, OUTER_RAD, BG_OUTER);
        RenderUtil.drawRoundedRectOutline(0, 0, width, height, OUTER_RAD, 1f,
                new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 90));

        float cardX = PAD_OUT, cardY = PAD_OUT;
        RenderUtil.drawRoundedRect(cardX, cardY, contentW, graphCardH, INNER_RAD, BG_INNER);
        RenderUtil.drawRoundedRectOutline(cardX, cardY, contentW, graphCardH, INNER_RAD, 0.7f, BORDER_IN);

        float cursor = cardY + PAD_IN;
        FontUtil.drawString("Movement Speed", cardX + PAD_IN, cursor, SZ_LABEL, TEXT_MUTED, false);
        String avgText = getAverageSpeed() + " bps avg";
        FontUtil.drawString(avgText,
                cardX + contentW - PAD_IN - FontUtil.getStringWidth(avgText, SZ_LABEL),
                cursor, SZ_LABEL, ac, false);
        cursor += FontUtil.getFontHeight(SZ_LABEL) + 8;
        drawSpeedPlot(cardX + PAD_IN, cursor, contentW - 2 * PAD_IN, 50, ac);

        GL11.glPopMatrix();
        return new double[]{width * scale, height * scale};
    }

    // ── Speed plot (unchanged logic) ──────────────────────────────────────
    private static void drawSpeedPlot(float x, float y, float w, float h, Color accent) {
        RenderUtil.drawRoundedRect(x, y, w, h, 6, new Color(15, 15, 20, 180));
        RenderUtil.drawRoundedRectOutline(x, y, w, h, 6, 0.5f, BORDER_IN);

        if (speeds.size() < 2) return;

        float plotBottom = y + h - 3;
        RenderUtil.beginRender();
        RenderUtil.beginAddingVertex(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        RenderUtil.glColor(accent);
        GL11.glLineWidth(2f);

        float length = w / (speeds.size() - 1);
        for (int i = 0; i < speeds.size(); i++) {
            float bps = speeds.get(i) * 50;
            float sx = x + i * length;
            float sy = plotBottom - Math.min(bps / 8f, 1f) * (h - 6);
            RenderUtil.addVertex(sx, sy);
        }
        RenderUtil.finishRender();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static double getAverageSpeed() {
        double average = speeds.stream().collect(Collectors.averagingDouble(value -> value.doubleValue() * 50));
        return Math.round(average * 100) / 100.0;
    }

    private static String formatPlayTime(int[] playTime) {
        int h = playTime[0], m = playTime[1], s = playTime[2];
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(":");
        if (m < 10) sb.append("0");
        sb.append(m).append(":");
        if (s < 10) sb.append("0");
        sb.append(s);
        return sb.toString();
    }

    private static void updateSize() {
        statistics.put("Games Played", (double) gamesPlayed);
        statistics.put("Victories",    (double) victoryCount);
        statistics.put("K/D",          getKillDeathRatio());
        statistics.put("Kills",        (double) killCount);
    }

    private static double getKillDeathRatio() {
        return deathCount == 0
                ? (double) killCount
                : Math.round((double) killCount / deathCount * 100) / 100.0;
    }

    // ── Existing event handlers — UNCHANGED ───────────────────────────────
    @SubscribeEvent
    public static void onChat(PacketEvent.Receive event) {
        if (C.mc.thePlayer == null) return;
        if (!(event.packet instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.packet;
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(
                packet.getChatComponent().getUnformattedText());
        String messageStr = packet.getChatComponent().toString();

        if (!message.contains(":") &&
                Arrays.stream(KILL_TRIGGERS).anyMatch(message.replace(C.mc.thePlayer.getName(), "*")::contains)) {
            killCount++;
        }
        String lowerMsg = message.toLowerCase();
        if (!message.contains(":") && WIN_MESSAGE_PATTERN.matcher(lowerMsg).matches()) {
            if (System.currentTimeMillis() - lastVictoryTime > 5000) {
                victoryCount++;
                lastVictoryTime = System.currentTimeMillis();
            }
        }
        if (messageStr.contains("ClickEvent{action=RUN_COMMAND, value='/play ") ||
                messageStr.contains("Want to play again?")) {
            gamesPlayed++;
        }
        if (message.contains("You died!")) {
            deathCount++;
        }
        updateSize();
    }

    @SubscribeEvent
    public static void onMotion(MotionEvent event) {
        if (speeds.size() >= 100) speeds.remove(0);
        speeds.add(getPlayerSpeed());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (endTime == -1 && (!C.mc.isSingleplayer() && C.mc.getCurrentServerData() == null)) {
            endTime = System.currentTimeMillis();
        } else if (endTime != -1 && (C.mc.isSingleplayer() || C.mc.getCurrentServerData() != null)) {
            reset();
        }
    }

    private static float getPlayerSpeed() {
        double bps = (Math.hypot(C.p().posX - C.p().prevPosX,
                C.p().posZ - C.p().prevPosZ) * TimerUtil.getTimer()) * 20;
        return (float) bps / 50;
    }

    public static int[] getPlayTime() {
        long diff = getTimeDiff();
        long diffSeconds = 0, diffMinutes = 0, diffHours = 0;
        if (diff > 0) {
            diffSeconds = diff / 1000 % 60;
            diffMinutes = diff / (60 * 1000) % 60;
            diffHours   = diff / (60 * 60 * 1000) % 24;
        }
        return new int[]{(int) diffHours, (int) diffMinutes, (int) diffSeconds};
    }

    public static long getTimeDiff() {
        return (endTime == -1 ? System.currentTimeMillis() : endTime) - startTime;
    }

    public static void reset() {
        startTime = System.currentTimeMillis();
        endTime   = -1;
        gamesPlayed = 0;
        killCount   = 0;
        deathCount  = 0;
        victoryCount = 0;
        updateSize();
    }

    @Override protected void onEnable()  { speeds.clear(); }
    @Override protected void onDisable() {}
}
