package com.github.cooldood.modules.impl.client;

import com.github.cooldood.modules.*;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.RenderUtil;
import com.github.cooldood.utils.render.draggable.Draggable;
import java.awt.Color;

@RegisterModule(
        name = "HUD",
        description = "Heads up display for client information.",
        category = Category.CLIENT
)
public class HUD extends Module {

    public static String CLIENT_NAME = "Coolware";

    // ── Ping cache ─────────────────────────────────────────────────────────
    private static long lastPingTime = 0;
    private static int  cachedPing   = 0;

    private static int getPing() {
        if (C.mc.isSingleplayer()) return 0;
        long now = System.currentTimeMillis();
        if (now - lastPingTime > 5000) {
            lastPingTime = now;
            if (C.mc.getNetHandler() != null && C.p() != null) {
                net.minecraft.client.network.NetworkPlayerInfo info =
                        C.mc.getNetHandler().getPlayerInfo(C.p().getUniqueID());
                if (info != null) cachedPing = info.getResponseTime();
            }
        }
        return cachedPing;
    }

    // ── Colors ─────────────────────────────────────────────────────────────
    private static final Color PILL_BG     = new Color(18, 20, 30, 210);    // dark navy
    private static final Color PILL_BORDER = new Color(255, 255, 255, 20);  // barely-visible white
    private static final Color TEXT_WHITE  = new Color(226, 229, 237, 255); // near-white
    private static final Color ICON_COLOR  = new Color(53, 214, 208, 255);  // #35D6D0 teal

    // ── Layout ─────────────────────────────────────────────────────────────
    private static final int   FONT      = 8;    // font size → ~7-8 px visual height
    private static final float PILL_H    = 16f;  // pill height
    private static final float RADIUS    = 5f;   // corner radius
    private static final float PAD_X     = 6f;   // horiz padding per side inside pill
    private static final float ICON_SIZE = 5f;   // small square icon side length
    private static final float ICON_GAP  = 4f;   // gap between icon and text
    private static final float PILL_GAP  = 3f;   // gap between pills
    private static final float ROW_GAP   = 4f;   // vertical gap between rows

    /**
     * Draws a small cyan rounded-square "icon dot" inside the pill.
     * Using drawRoundedRect (same API as the pill bg) so it always renders correctly
     * — no font atlas dependency at all.
     *
     * @param cx  center-x of the icon
     * @param cy  center-y of the icon
     */
    private static void drawIconDot(float cx, float cy) {
        float half = ICON_SIZE / 2f;
        RenderUtil.drawRoundedRect(cx - half, cy - half, ICON_SIZE, ICON_SIZE, 1.5f, ICON_COLOR);
    }

    /**
     * Draws one pill at (x, y).
     *
     * @param x       left edge of pill
     * @param y       top edge of pill
     * @param label   text to display
     * @param showIcon whether to draw the cyan icon dot before the text
     * @return pill width
     */
    private static float drawPill(float x, float y, String label, boolean showIcon) {
        float fontH  = FontUtil.getFontHeight(FONT);
        float labelW = FontUtil.getStringWidth(label, FONT);

        // Content = [ICON_SIZE + ICON_GAP] (if icon) + labelW
        float contentW = showIcon ? (ICON_SIZE + ICON_GAP + labelW) : labelW;
        float pillW    = PAD_X + contentW + PAD_X;

        // Draw pill background
        RenderUtil.drawRoundedRect(x, y, pillW, PILL_H, RADIUS, PILL_BG);
        // Draw pill border
        RenderUtil.drawRoundedRectOutline(x, y, pillW, PILL_H, RADIUS, 0.7f, PILL_BORDER);

        // Vertical centers
        float cy = y + PILL_H / 2f;              // vertical center of pill
        float ty = y + (PILL_H - fontH) / 2f;   // top of text so it's v-centered

        if (showIcon) {
            // Icon dot — positioned at left edge + PAD_X, vertically centered
            float iconCX = x + PAD_X + ICON_SIZE / 2f;
            drawIconDot(iconCX, cy);
            // Label text — right of the icon
            FontUtil.drawString(label, x + PAD_X + ICON_SIZE + ICON_GAP, ty, FONT, TEXT_WHITE, false);
        } else {
            FontUtil.drawString(label, x + PAD_X, ty, FONT, TEXT_WHITE, false);
        }

        return pillW;
    }

    // ── Watermark draggable ────────────────────────────────────────────────
    public static Draggable coolwareWatermark = new Draggable(
            "CoolWareWatermark",
            () -> {
                // Gather dynamic values
                String clientName = CLIENT_NAME;
                String username   = C.p() != null ? C.p().getName() : "---";
                int    fps        = C.mc.getDebugFPS();
                int    ping       = getPing();
                String fpsStr     = fps  + " Fps";
                String pingStr    = ping + " Ping";

                String coordStr = "";
                if (C.p() != null) {
                    int bx = (int) C.p().posX;
                    int by = (int) C.p().posY;
                    int bz = (int) C.p().posZ;
                    coordStr = bx + " " + by + " " + bz;
                }

                // ── Row 1: [* ClientName] [* Username] [* FPS] ────────────
                float rx = 0f, ry = 0f;

                float w1 = drawPill(rx, ry, clientName, true); rx += w1 + PILL_GAP;
                float w2 = drawPill(rx, ry, username,   true); rx += w2 + PILL_GAP;
                float w3 = drawPill(rx, ry, fpsStr,     true); rx += w3;

                float totalW = rx;
                float totalH = PILL_H;

                // ── Row 2: [* Coords] [* Ping] ────────────────────────────
                boolean hasRow2 = C.p() != null
                        && (!coordStr.isEmpty() || !C.mc.isSingleplayer());

                if (hasRow2) {
                    float rx2 = 0f;
                    float ry2 = PILL_H + ROW_GAP;

                    if (!coordStr.isEmpty()) {
                        float wc = drawPill(rx2, ry2, coordStr, true); rx2 += wc + PILL_GAP;
                    }
                    if (!C.mc.isSingleplayer()) {
                        float wp = drawPill(rx2, ry2, pingStr, true); rx2 += wp;
                    }

                    totalW = Math.max(totalW, rx2);
                    totalH = PILL_H * 2 + ROW_GAP;
                }

                return new double[]{totalW, totalH};
            },
            e -> ModuleManager.isEnabled(HUD.class),
            e -> true
    );

    @Override protected void onEnable()  {}
    @Override protected void onDisable() {}
}
