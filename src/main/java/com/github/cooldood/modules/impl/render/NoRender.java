package com.github.cooldood.modules.impl.render;

import com.github.cooldood.modules.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.awt.*;

@RegisterModule(
        name = "No Render",
        description = "Provides No Render functionality for the client.",
        category = Category.RENDER,
        enabledByDefault = true
)
public class NoRender extends Module {
    @RegisterSubModule(name = "Fire Modifier")
    public static boolean fireModifier = true;

    @RegisterSubModule(name = "Fire Colour", parent = "Fire Modifier")
    public static Color fireColour = new Color(255, 255, 255, 100);

    @RegisterSubModule(name = "Fire Offset", min = -2, max = 2, parent = "Fire Modifier")
    public static double fireOffset = 0;

    @RegisterSubModule(name = "Show Invisibles")
    public static boolean showInvisibles = true;
    @RegisterSubModule(name = "Only Invis Players")
    public static boolean onlyInvisPlayers = true;

    @RegisterSubModule(name = "No Blindness")
    public static boolean noBlindness = true;

    @RegisterSubModule(name = "Custom Blindness Fog", parent = "No Blindness")
    public static boolean customBlindnessFog = true;
    @RegisterSubModule(name = "Blindness Fog Colour", parent = "Custom Blindness Fog")
    public static Color blindnessFogColour = new Color(0, 0, 0, 100);

    @RegisterSubModule(name = "No Nausea")
    public static boolean noNausea = true;

    public static boolean showInvisible(Entity entity) {
        return ModuleManager.isEnabled(NoRender.class) && showInvisibles && (!onlyInvisPlayers || entity instanceof EntityPlayer);
    }

    public static float fogDistance() {
        return (255-blindnessFogColour.getAlpha()) / 5f + 1;
    }

    public static boolean noBlindness() {
        return ModuleManager.isEnabled(NoRender.class) && noBlindness;
    }

    public static boolean noNausea() {
        return ModuleManager.isEnabled(NoRender.class) && noNausea;
    }

    private static final Color DEFAULT_FIRE = new Color(1.0F, 1.0F, 1.0F, 0.9F);
    public static Color fireColour() {
        return ModuleManager.isEnabled(NoRender.class) && fireModifier ? fireColour : DEFAULT_FIRE;
    }

    public static double getFireOffset() {
        return ModuleManager.isEnabled(NoRender.class) && fireModifier ? fireOffset : 0;
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}
