package com.github.cooldood.modules.impl.render;

import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.PacketEvent;
import com.github.cooldood.modules.*;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.minecraft.TargetUtil;
import com.github.cooldood.utils.render.EasingUtil;
import com.github.cooldood.utils.render.FontUtil;
import com.github.cooldood.utils.render.RenderUtil;
import com.github.cooldood.utils.render.draggable.Draggable;
import lombok.AllArgsConstructor;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MathHelper;

import java.awt.*;

@RegisterModule(
        name = "Target HUD",
        description = "Provides Target HUD functionality for the client.",
        category = Category.RENDER
)
public class TargetHUD extends Module {
    public static final com.github.cooldood.utils.tenacity.animations.ContinualAnimation healthAnimation = new com.github.cooldood.utils.tenacity.animations.ContinualAnimation();
    @RegisterSubModule(name = "Mode")
    public static HUDMode hudMode = HUDMode.Tenacity;

    public enum HUDMode {
        Scalehack,
        Tenacity,
        Rise,
        Novoline
    }

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

    public static Draggable targetHUD = new Draggable(
            "targetHUD",
            () -> {
                double easingIn = EasingUtil.getAnimation("thIn");
                double easingOut = EasingUtil.getAnimation("thOut");

                if (target == null) return new double[] {0,0};

                if (target.lastInteract + popInTime + targetTimeBeforeDementiaKicksIn + popOutTime <= System.currentTimeMillis()) {
                    target = null;
                    return new double[] {0,0};
                }
                else if (target.lastInteract + popInTime + targetTimeBeforeDementiaKicksIn <= System.currentTimeMillis() && easingOut == -1) {
                    EasingUtil.addAnimation("thOut", popOutTime, true, easeOutFunction);
                }

                float healthNumber = target.entity.getHealth() + TargetUtil.getAbsorption(target.entity);
                float healthPercentage = MathHelper.clamp_float(healthNumber / target.entity.getMaxHealth(), 0, 1);

                double distance = TargetUtil.getDistanceToEntity(target.entity);

                float width = 120;
                float height = 50;

                // Adjust sizing based on the selected mode
                switch (hudMode) {
                    case Scalehack:
                        String healthString = String.valueOf(Math.round(healthNumber * 10.0) / 10.0);
                        String distanceString = String.valueOf(Math.round(distance * 10.0) / 10.0);
                        String blockingString = target.entity instanceof AbstractClientPlayer && ((AbstractClientPlayer) target.entity).isBlocking() ? "§cBlocking" : "§aUnblocked";
                        float healthStringWidth = FontUtil.getStringWidth(healthString + " | ", 6);
                        float distanceStringWidth = FontUtil.getStringWidth(distanceString + " | ", 6);
                        float blockingStringWidth = FontUtil.getStringWidth(blockingString, 6);
                        float infoStringWidth = healthStringWidth + distanceStringWidth + blockingStringWidth;

                        width = Math.max(FontUtil.getStringWidth(target.entity.getName(), 15), infoStringWidth) + 37;
                        height = FontUtil.getFontHeight(15) + FontUtil.getFontHeight(6) + 4;
                        break;
                    case Tenacity:
                        width = Math.max(155, FontUtil.getStringWidth(target.entity.getName(), 12) + 75);
                        height = 50;
                        break;
                    case Rise:
                        width = Math.max(128, FontUtil.getStringWidth("Name: " + target.entity.getName(), 10) + 60);
                        height = 50;
                        break;
                    case Novoline:
                        width = Math.max(120, FontUtil.getStringWidth(target.entity.getName(), 10) + 50);
                        height = 34;
                        break;
                }

                double xIn = easingIn == -1 ? 0 : (-C.res().getScaledWidth() - width) * (1-easingIn);
                double xOut = easingOut == -1 ? 0 : (C.res().getScaledWidth() + width) * (easingOut);

                float x = (float) (xIn + xOut);
                float y = 0;

                float alpha = 1f;
                if (easingOut != -1) {
                    alpha = (float) (1 - easingOut);
                } else if (easingIn != -1) {
                    alpha = (float) easingIn;
                }
                alpha = MathHelper.clamp_float(alpha, 0f, 1f);
                int alphaInt = (int) (alpha * 255);

                switch (hudMode) {
                    case Scalehack:
                        String healthString = String.valueOf(Math.round(healthNumber * 10.0) / 10.0);
                        String distanceString = String.valueOf(Math.round(distance * 10.0) / 10.0);
                        String blockingString = target.entity instanceof AbstractClientPlayer && ((AbstractClientPlayer) target.entity).isBlocking() ? "§cBlocking" : "§aUnblocked";
                        float healthStringWidth = FontUtil.getStringWidth(healthString + " | ", 6);
                        float distanceStringWidth = FontUtil.getStringWidth(distanceString + " | ", 6);

                        RenderUtil.drawBlurRect(x, y, width, height, 3);
                        RenderUtil.drawRect(x, y, width, height, new Color(22, 22, 22, (int)(100 * alpha)));
                        RenderUtil.drawPlayerHead(x + 4, y + 4, 24, 24, Color.WHITE, target.entity);

                        Color[] colorsFade = RenderUtil.getColorsFade(x, width, RenderUtil.ThemeColours.Gay.getColours(), 1);
                        RenderUtil.drawGradientLR(x, y, width, 1, colorsFade[0], colorsFade[1]);

                        FontUtil.drawString(target.entity.getName(), x + 32, y, 15, new Color(255, 255, 255, alphaInt), true);
                        Color healthColour = RenderUtil.getProgressColour(healthPercentage);
                        Color distanceColour = RenderUtil.getProgressColour((float) (1 - (distance / 6f)));
                        FontUtil.drawString(healthString + "§f | ", x + 32, y + FontUtil.getFontHeight(15), 6, new Color(healthColour.getRed(), healthColour.getGreen(), healthColour.getBlue(), alphaInt), true);
                        FontUtil.drawString(distanceString + "§f | ", x + 32 + healthStringWidth, y + FontUtil.getFontHeight(15), 6, new Color(distanceColour.getRed(), distanceColour.getGreen(), distanceColour.getBlue(), alphaInt), true);
                        FontUtil.drawString(blockingString, x + 32 + healthStringWidth + distanceStringWidth, y + FontUtil.getFontHeight(15), 6, new Color(255, 255, 255, alphaInt), true);
                        break;

                    case Tenacity:
                        // Claymorphic background
                        RenderUtil.drawRoundedRect(x + 1, y + 1, width, height, 6, new Color(0, 0, 0, (int) (80 * alpha))); // shadow
                        RenderUtil.drawRoundedRect(x, y, width, height, 6, new Color(26, 26, 34, (int) (220 * alpha))); // body
                        RenderUtil.drawRoundedRect(x + 1, y + 1, width - 2, 2, 6, new Color(255, 255, 255, (int) (22 * alpha))); // rim highlight

                        // Left accent bar
                        RenderUtil.drawRoundedRect(x + 2, y + 4, 3, height - 8, 2, new Color(140, 140, 140, (int) (200 * alpha)));

                        // Player skin
                        RenderUtil.drawPlayerHead(x + 10, y + (height / 2f) - 19, 38, 38, Color.WHITE, target.entity);

                        // Target name
                        FontUtil.drawString(target.entity.getName(), x + 54, y + 8, 12, new Color(255, 255, 255, alphaInt), true);

                        // Health Bar
                        float barW = width - 64;
                        RenderUtil.drawRoundedRect(x + 54, y + 23, barW, 4, 2, new Color(55, 55, 70, (int) (255 * alpha)));
                        if (healthPercentage > 0) {
                            RenderUtil.drawRoundedRect(x + 54, y + 23, barW * healthPercentage, 4, 2, new Color(150, 150, 150, (int) (255 * alpha)));
                        }

                        // Info text
                        String infoStr = Math.round(healthPercentage * 100) + "% - " + (Math.round(distance * 10.0) / 10.0) + "m";
                        FontUtil.drawString(infoStr, x + 54, y + 32, 9, new Color(200, 200, 200, alphaInt), true);
                        break;

                    case Rise:
                        // Transparent dark card
                        RenderUtil.drawRoundedRect(x, y, width, height, 6, new Color(0, 0, 0, (int) (110 * alpha)));

                        // Face
                        RenderUtil.drawPlayerHead(x + 5, y + 5, 30, 30, Color.WHITE, target.entity);

                        // Details
                        FontUtil.drawString("Name: " + target.entity.getName(), x + 40, y + 8, 10, new Color(255, 255, 255, alphaInt), true);
                        FontUtil.drawString("Distance: " + (Math.round(distance * 10.0) / 10.0) + " Hurt: " + target.entity.hurtTime, x + 40, y + 20, 9, new Color(200, 200, 200, alphaInt), true);

                        // Health bar (full width minus padding)
                        float riseBarW = width - 10;
                        RenderUtil.drawRoundedRect(x + 5, y + 38, riseBarW, 5, 2.5f, new Color(30, 30, 35, (int) (255 * alpha)));
                        if (healthPercentage > 0) {
                            RenderUtil.drawRoundedRect(x + 5, y + 38, riseBarW * healthPercentage, 5, 2.5f, new Color(140, 140, 140, (int) (255 * alpha)));
                        }

                        // Health text
                        String healthVal = String.valueOf(Math.round(healthNumber * 10.0) / 10.0);
                        FontUtil.drawString(healthVal, x + 5 + (riseBarW * healthPercentage) + 4, y + 36, 8, new Color(255, 255, 255, alphaInt), true);
                        break;

                    case Novoline:
                        // Double outlines
                        RenderUtil.drawRoundedRect(x, y, width, height, 4, new Color(29, 29, 29, (int) (255 * alpha)));
                        RenderUtil.drawRoundedRectOutline(x + 1, y + 1, width - 2, height - 2, 3, 1, new Color(40, 40, 40, (int) (255 * alpha)));

                        // Face
                        RenderUtil.drawPlayerHead(x + 3.5f, y + 3f, 28, 28, Color.WHITE, target.entity);

                        // Target name
                        FontUtil.drawString(target.entity.getName(), x + 34, y + 4, 10, new Color(255, 255, 255, alphaInt), true);

                        // Health bar track
                        RenderUtil.drawRoundedRect(x + 34, y + 15, 83, 10, 2, new Color(39, 30, 29, (int) (255 * alpha)));
                        // Health bar fill
                        if (healthPercentage > 0) {
                            RenderUtil.drawRoundedRect(x + 34, y + 15, 83 * healthPercentage, 10, 2, new Color(130, 130, 130, (int) (255 * alpha)));
                        }

                        // Health percent text
                        String novoPercent = Math.round(healthPercentage * 100) + "%";
                        float textWidth = FontUtil.getStringWidth(novoPercent, 8);
                        FontUtil.drawString(novoPercent, x + 34 + 41.5f - textWidth / 2f, y + 16, 8, new Color(255, 255, 255, alphaInt), true);
                        break;
                }

                return new double[] {x + width, y + height};
            },
            e -> ModuleManager.isEnabled(TargetHUD.class),
            e -> target != null || EasingUtil.getAnimation("thIn") != -1 || EasingUtil.getAnimation("thOut") != -1
    );

    private static void updateTarget(EntityLivingBase entity) {
        EasingUtil.Animation easingOutDetails = EasingUtil.getAnimationDetails("thOut");
        boolean isEasingIn = EasingUtil.getAnimationDetails("thIn") != null;
        boolean isEasingOut = easingOutDetails != null && easingOutDetails.up;

        if (target == null && !isEasingOut && !isEasingIn) EasingUtil.addAnimation("thIn", popInTime, true, easeInFunction);
        if (target == null || target.entity != entity) target = new Target(entity, System.currentTimeMillis());
        if (isEasingOut) EasingUtil.addAnimation("thOut", popInTime, false, easeOutFunction);

        target.lastInteract = System.currentTimeMillis();
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}
