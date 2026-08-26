package com.github.cooldood.modules.impl.render;

import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.PacketEvent;
import com.github.cooldood.modules.*;
import com.github.cooldood.modules.impl.client.ThemeModule;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.minecraft.TargetUtil;
import com.github.cooldood.utils.render.EasingUtil;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.IconFont;
import com.github.cooldood.utils.render.RenderUtil;
import com.github.cooldood.utils.render.draggable.Draggable;
import com.github.cooldood.utils.tenacity.animations.ContinualAnimation;
import lombok.AllArgsConstructor;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MathHelper;

import java.awt.Color;

@RegisterModule(
        name = "Target HUD",
        description = "Provides Target HUD functionality for the client.",
        category = Category.RENDER
)
public class TargetHUD extends Module {

    public static final ContinualAnimation healthAnimation = new ContinualAnimation();

    @RegisterSubModule(name = "Target Time", description = "Time before the module forgets its target", max = 5000, increment = 50)
    public static long targetTimeBeforeDementiaKicksIn = 250;

    @RegisterSubModule(name = "Animation In")
    public static SubCategory animationIn = new SubCategory();

    @RegisterSubModule(name = "Ease In", parent = "Animation In")
    public static EasingUtil.EasingFunctions easeInFunction = EasingUtil.EasingFunctions.Ease_In_Out_Expo;

    @RegisterSubModule(name = "Pop In Time", description = "Time for the pop-in effect to finish", max = 1000, increment = 50, parent = "Animation In")
    public static long popInTime = 250;

    @RegisterSubModule(name = "Animation Out")
    public static SubCategory animationOut = new SubCategory();

    @RegisterSubModule(name = "Ease Out", parent = "Animation Out")
    public static EasingUtil.EasingFunctions easeOutFunction = EasingUtil.EasingFunctions.Ease_In_Out_Expo;

    @RegisterSubModule(name = "Pop Out Time", description = "Time for the pop-out effect to finish", max = 1000, increment = 50, parent = "Animation Out")
    public static long popOutTime = 250;

    private static Target target;

    @AllArgsConstructor
    private static class Target {
        public EntityLivingBase entity;
        public long lastInteract;
    }

    @SubscribeEvent
    public static void registerTarget(PacketEvent.Send event) {
        if (C.mc.currentScreen instanceof GuiChat) {
            updateTarget(C.p());
            return;
        }

        if (!(event.packet instanceof C02PacketUseEntity)) return;

        C02PacketUseEntity attackPacket = (C02PacketUseEntity) event.packet;
        if (attackPacket.getAction() != C02PacketUseEntity.Action.ATTACK) return;

        Entity newTarget = attackPacket.getEntityFromWorld(C.w());
        if (!(newTarget instanceof EntityLivingBase) || !TargetUtil.isValidTarget(newTarget, true)) return;

        updateTarget((EntityLivingBase) newTarget);
    }

    // ── Layout Constants (Flat, Dark, ClickGUI Style) ────────────────────────
    private static final Color BG_COLOR    = new Color(13, 13, 21); // Same dark slate as ClickGUI
    private static final float PAD         = 6f;
    private static final float INNER_PAD   = 6f;
    private static final float HEAD_SIZE   = 32f;
    private static final float BAR_H       = 3.5f;
    private static final int   NAME_SIZE   = 13;
    private static final int   INFO_SIZE   = 10;

    public static Draggable targetHUD = new Draggable(
            "targetHUD",
            () -> {
                double easingIn  = EasingUtil.getAnimation("thIn");
                double easingOut = EasingUtil.getAnimation("thOut");

                if (target == null) return new double[] {0, 0};

                if (target.lastInteract + popInTime + targetTimeBeforeDementiaKicksIn + popOutTime <= System.currentTimeMillis()) {
                    target = null;
                    return new double[] {0, 0};
                } else if (target.lastInteract + popInTime + targetTimeBeforeDementiaKicksIn <= System.currentTimeMillis() && easingOut == -1) {
                    EasingUtil.addAnimation("thOut", popOutTime, true, easeOutFunction);
                }

                EntityLivingBase entity = target.entity;

                // ── Data & Strings ────────────────────────────────────────────
                float healthRaw    = entity.getHealth();
                float maxHealth    = Math.max(entity.getMaxHealth(), 1f);
                float healthNumber = Math.round(healthRaw * 10.0f) / 10.0f;

                double distRaw     = TargetUtil.getDistanceToEntity(entity);
                float distNumber   = Math.round(distRaw * 10.0) / 10.0f;

                String name        = entity.getName();
                String healthStr   = String.valueOf(healthNumber);
                String distStr     = String.valueOf(distNumber);
                String infoPrefix  = healthStr + "  -  " + distStr + " ";

                // ── Panel sizing ──────────────────────────────────────────────
                float nameW     = FontUtil.getStringWidth(name, NAME_SIZE);
                float infoTextW = FontUtil.getStringWidth(infoPrefix, INFO_SIZE);
                float heartW    = IconFont.getWidth(IconFont.HEART, INFO_SIZE);
                float infoW     = infoTextW + heartW;
                float textColW  = Math.max(nameW, infoW);
                float panelW    = Math.max(PAD + HEAD_SIZE + INNER_PAD + textColW + PAD, 130f);

                float nameH     = FontUtil.getFontHeight(NAME_SIZE);
                float infoH     = FontUtil.getFontHeight(INFO_SIZE);
                // Vertical block: name + 2px gap + bar + 2px gap + info
                float textColH  = nameH + 2f + BAR_H + 2f + infoH;
                float panelH    = Math.max(HEAD_SIZE + PAD * 2f, textColH + PAD * 2f);

                // ── Slide animation ───────────────────────────────────────────
                double xIn  = easingIn  == -1 ? 0 : (-C.res().getScaledWidth() - panelW) * (1.0 - easingIn);
                double xOut = easingOut == -1 ? 0 : ( C.res().getScaledWidth() + panelW) * easingOut;

                float x = (float)(xIn + xOut);
                float y = 0f;

                // Alpha — fade during slide-in / slide-out
                float alpha;
                if      (easingOut != -1) alpha = (float)(1.0 - easingOut);
                else if (easingIn  != -1) alpha = (float) easingIn;
                else                      alpha = 1f;
                alpha    = MathHelper.clamp_float(alpha, 0f, 1f);
                int aInt = (int)(alpha * 255);

                // ── Theme accent ──────────────────────────────────────────────
                Color accent = ThemeModule.primaryColor;

                // ── Background ────────────────────────────────────────────────
                // Flat square rectangle — matches ClickGUI PANEL_RADIUS
                RenderUtil.drawRect(x, y, panelW, panelH,
                        new Color(BG_COLOR.getRed(), BG_COLOR.getGreen(), BG_COLOR.getBlue(),
                                (int)(210 * alpha)));

                // 1 px purple accent line at the bottom — same as ClickGUI drawClayPanel
                RenderUtil.drawRect(x, y + panelH - 1f, panelW, 1f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                                (int)(180 * alpha)));

                // ── Player head ───────────────────────────────────────────────
                float headX = x + PAD;
                float headY = y + (panelH - HEAD_SIZE) / 2f;
                RenderUtil.drawPlayerHead(headX, headY, HEAD_SIZE, HEAD_SIZE,
                        new Color(255, 255, 255, aInt), entity);

                // ── Text column ───────────────────────────────────────────────
                float colX      = x + PAD + HEAD_SIZE + INNER_PAD;
                // Vertically centre the text block
                float blockTopY = y + (panelH - textColH) / 2f;

                // Player name — near-white
                FontUtil.drawString(name, colX, blockTopY, NAME_SIZE,
                        new Color(230, 230, 235, aInt), true);

                // ── Health bar ────────────────────────────────────────────────
                float barY = blockTopY + nameH + 2f;
                float barW = panelW - PAD - HEAD_SIZE - INNER_PAD - PAD;

                // Track (dark background strip)
                RenderUtil.drawRect(colX, barY, barW, BAR_H,
                        new Color(30, 30, 42, (int)(160 * alpha)));

                // Smooth health animation
                healthAnimation.animate(
                        MathHelper.clamp_float(healthRaw / maxHealth, 0f, 1f),
                        18
                );
                float animatedFraction = MathHelper.clamp_float(
                        healthAnimation.getOutput(), 0f, 1f
                );

                // Health fill
                float fillW = barW * animatedFraction;
                if (fillW > 0.5f) {
                    RenderUtil.drawRect(colX, barY, fillW, BAR_H,
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), aInt));
                }

                // ── Info text: "<health>  -  <distance> " + Heart Icon ────────
                float infoY = barY + BAR_H + 2f;
                FontUtil.drawString(infoPrefix, colX, infoY, INFO_SIZE,
                        new Color(155, 155, 175, aInt), true);

                float heartIconH = IconFont.getHeight(INFO_SIZE);
                float heartIconY = infoY + (infoH - heartIconH) / 2f;
                IconFont.drawIcon(IconFont.HEART, colX + infoTextW, heartIconY, INFO_SIZE,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), aInt));

                return new double[]{panelW, panelH};
            },
            e -> {
                if (C.mc.currentScreen instanceof GuiChat) {
                    updateTarget(C.p());
                }
                return ModuleManager.isEnabled(TargetHUD.class) && target != null;
            },
            e -> true
    );

    public static void updateTarget(EntityLivingBase entity) {
        double easingIn = EasingUtil.getAnimation("thIn");
        if (target == null && easingIn == -1) {
            EasingUtil.addAnimation("thIn", popInTime, false, easeInFunction);
            healthAnimation.animate(MathHelper.clamp_float(entity.getHealth() / entity.getMaxHealth(), 0f, 1f), 18);
        }

        target = new Target(entity, System.currentTimeMillis());
    }

    @Override
    protected void onEnable() {
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
    }
}
