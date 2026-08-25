package com.github.cooldood.utils.render.notifications;

import com.github.cooldood.modules.impl.client.ThemeModule;
import com.github.cooldood.modules.impl.render.Notifications;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
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

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();

        // 1. Enforce the correct GUI Projection Matrix in case RenderTickEvent is using a different state
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0D, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);

        // 2. Disable Scissor Test if another module left it enabled
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (wasScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);

        float scale = (float) Notifications.size;
        Notifications.Position pos = Notifications.position;
        boolean isRight = (pos == Notifications.Position.TopRight || pos == Notifications.Position.BottomRight);
        boolean isTop = (pos == Notifications.Position.TopRight || pos == Notifications.Position.TopLeft);

        float yOffset = isTop ? 15 : screenHeight - 15;

        // Iterate backwards for stacking top-down or bottom-up (newest at top or bottom)
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

            float targetX = isRight ? screenWidth - effectiveWidth - 10 : 10;
            float targetY = isTop ? yOffset : yOffset - effHeight;

            // Handle entry/exit
            if (notif.isExpired() || notif.getTimeLeft() < 300) {
                targetX = isRight ? screenWidth + 20 : -effectiveWidth - 20; // Slide off
                if (isRight ? notif.getX() >= screenWidth : notif.getX() <= -effectiveWidth) {
                    notifications.remove(i);
                    continue; // Skip rendering
                }
            }

            // Initialization (flag is -1, -1)
            if (notif.getX() == -1 && notif.getY() == -1) {
                notif.setX(isRight ? screenWidth + 20 : -effectiveWidth - 20); // Start off-screen
                notif.setY(targetY);          // At exact Y position
            }

            notif.animate(targetX, targetY);

            drawModernNotification(notif.getX(), notif.getY(), effectiveWidth, effHeight, scale, effTitleSize, effDescSize, effPadX, notif);
            
            // Advance Y for next notification
            float gap = effHeight + (6 * scale);
            if (isTop) {
                yOffset += gap; // Stack downwards
            } else {
                yOffset -= gap; // Stack upwards
            }
        }

        // Restore OpenGL state
        if (wasScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
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
