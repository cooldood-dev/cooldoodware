package com.github.cooldood.utils.render.notifications;

import com.github.cooldood.modules.impl.client.ThemeModule;
import com.github.cooldood.modules.impl.render.Notifications;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class NotificationManager {
    private static final List<Notification> notifications = new ArrayList<>();

    // Modern Dark Theme Constants
    private static final Color BG_COLOR = new Color(13, 13, 21, 230); // Dark translucent
    private static final Color TITLE_COLOR = new Color(255, 255, 255);
    private static final Color DESC_COLOR = new Color(160, 160, 184);

    private static final float PAD_X = 7f;
    private static final float PAD_Y = 6f;
    private static final int TITLE_SIZE = 12;
    private static final int DESC_SIZE = 10;
    
    // Fixed modern dimensions
    private static final float NOTIF_HEIGHT = 32f;
    private static final float PROGRESS_HEIGHT = 1.5f;

    public static void post(String title, String description, NotificationType type, long durationMs) {
        notifications.add(new Notification(title, description, type, durationMs));
    }

    public static void render() {
        if (notifications.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        float screenWidth = sr.getScaledWidth();
        
        // Target start Y near top right
        float yOffset = 15;
        float scale = (float) Notifications.size;

        // Iterate backwards for stacking top-down (newest at top)
        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notif = notifications.get(i);

            int effTitleSize = (int)(TITLE_SIZE * scale);
            int effDescSize = (int)(DESC_SIZE * scale);
            float effPadX = PAD_X * scale;
            float effHeight = NOTIF_HEIGHT * scale;

            // Calculate width dynamically
            float titleWidth = FontUtil.getStringWidth(notif.getTitle(), effTitleSize);
            float descWidth = FontUtil.getStringWidth(notif.getDescription(), effDescSize);
            float effectiveWidth = Math.max(titleWidth, descWidth) + (effPadX * 2);
            // Minimum width to avoid looking squished
            if (effectiveWidth < 90 * scale) effectiveWidth = 90 * scale;

            float targetX = screenWidth - effectiveWidth - 10;
            float targetY = yOffset;

            // Handle entry/exit
            if (notif.isExpired() || notif.getTimeLeft() < 300) {
                targetX = screenWidth + 20; // Slide off right
                if (notif.getX() >= screenWidth) {
                    notifications.remove(i);
                    continue; // Skip rendering
                }
            }

            // Initialization (flag is -1, -1)
            if (notif.getX() == -1 && notif.getY() == -1) {
                notif.setX(screenWidth + 20); // Start off-screen right
                notif.setY(targetY);          // At exact Y position
            }

            notif.animate(targetX, targetY);

            drawModernNotification(notif.getX(), notif.getY(), effectiveWidth, effHeight, scale, effTitleSize, effDescSize, effPadX, notif);
            
            // Advance Y for next notification
            yOffset += effHeight + (6 * scale); // Stack gap
        }
    }

    private static void drawModernNotification(float x, float y, float w, float h, float scale, int effTitleSize, int effDescSize, float effPadX, Notification notif) {
        GL11.glPushMatrix();

        float effPadY = PAD_Y * scale;
        float effProgress = PROGRESS_HEIGHT * scale;

        // 1. Minimal Dark Background
        RenderUtil.drawRoundedRect(x, y, w, h, 2 * scale, BG_COLOR);

        // 2. Text (Title + Description) with drop shadows
        float textY = y + effPadY;
        FontUtil.drawString(notif.getTitle(), x + effPadX, textY, effTitleSize, TITLE_COLOR, true);
        
        textY += FontUtil.getFontHeight(effTitleSize) + (2 * scale);
        FontUtil.drawString(notif.getDescription(), x + effPadX, textY, effDescSize, DESC_COLOR, true);

        // 3. Thin Progress Bar (Bottom Edge)
        float progress = Math.max(0, Math.min(1, (float) notif.getTimeLeft() / notif.getMaxTime()));
        if (progress > 0) {
            Color accent = RenderUtil.getColorsFade((int)(y * 10), ThemeModule.getThemeColours(), 4f);
            RenderUtil.drawRoundedRect(x, y + h - effProgress, w * progress, effProgress, 0, accent);
        }

        GL11.glPopMatrix();
    }
}
