package com.github.cooldood.modules.impl.movement;

import com.github.cooldood.bridge.net.minecraft.client.settings.KeyBindingBridge;
import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.*;
import com.github.cooldood.modules.*;
import com.github.cooldood.modules.impl.client.ThemeModule;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.minecraft.*;
import com.github.cooldood.utils.render.EasingUtil;
import com.github.cooldood.utils.render.Render3dUtil;
import com.github.cooldood.utils.render.RenderUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RegisterModule(
        name = "Scaffold",
        description = "Automatically places blocks under you.",
        category = Category.MOVEMENT,
        dangerous = true
)
public class Scaffold extends Module {

    // --- Conditions ---
    @RegisterSubModule(name = "Conditions")
    public static SubCategory conditions = new SubCategory();
    @RegisterSubModule(name = "Blocks Only", description = "Only scaffold if holding blocks", parent = "Conditions")
    public static boolean blocksOnly = false;
    @RegisterSubModule(name = "Moving Backwards", description = "Only scaffold if holding back key", parent = "Conditions")
    public static boolean movingBackwards = false;
    @RegisterSubModule(name = "Right Click Down", description = "Only scaffold if right click is down", parent = "Conditions")
    public static boolean rightClickOnly = false;
    @RegisterSubModule(name = "Crouch Down", description = "Only scaffold if crouch key is down", parent = "Conditions")
    public static boolean crouchDownOnly = false;
    @RegisterSubModule(name = "Uncrouch", description = "Doesn't actually sneak while holding crouch", parent = "Crouch Down")
    public static boolean uncrouchAuto = true;
    @RegisterSubModule(name = "Pitch Range", description = "Only scaffold if in pitch range", parent = "Conditions")
    public static boolean pitchRange = false;
    @RegisterSubModule(name = "Min Pitch", parent = "Pitch Range", description = "-90 is looking straight up", min = -90, max = 90)
    public static int minPitch = 35;
    @RegisterSubModule(name = "Max Pitch", parent = "Pitch Range", description = "-90 is looking straight up", min = -90, max = 90)
    public static int maxPitch = 90;

    // --- Basics ---
    @RegisterSubModule(name = "Basics")
    public static SubCategory basicCategory = new SubCategory();
    @RegisterSubModule(name = "Block Place Reach", min = 2, max = 6, increment = 0.1, parent = "Basics")
    public static float blockReach = 4.5f;
    @RegisterSubModule(name = "Use Largest Stack", description = "Always switches to largest stack of blocks", parent = "Basics")
    public static boolean useLargestStack = false;
    @RegisterSubModule(name = "Swap Time", parent = "Use Largest Stack", description = "Blocks placed between switching blocks", min = 1, max = 10)
    public static int swapTime = 5;

    // --- Tower ---
    @RegisterSubModule(name = "Tower")
    public static SubCategory towerCategory = new SubCategory();
    @RegisterSubModule(name = "Tower Mode", parent = "Tower", description = "Tower mode")
    public static TowerMode towerMode = TowerMode.Legit;
    public enum TowerMode {
        None,
        Legit,
        Vanilla
    }
    @RegisterSubModule(name = "Only Off Ground", parent = "Tower Mode", modeParentString = {"Legit", "Vanilla"}, description = "Only tower if in air")
    public static boolean onlyOffGround = true;
    @RegisterSubModule(name = "Only If Space Down", parent = "Tower Mode", modeParentString = {"Legit", "Vanilla"}, description = "Only tower if holding your jump key")
    public static boolean onlyIfSpaceDown = true;
    @RegisterSubModule(name = "Tower Pitch Range", parent = "Tower Mode", modeParentString = {"Legit", "Vanilla"}, description = "Only tower within certain pitch range")
    public static boolean onlyTowerLookingUp = true;
    @RegisterSubModule(name = "Tower Min Pitch", parent = "Tower Pitch Range", description = "-90 is looking straight up", min = -90, max = 90)
    public static int towerMinPitch = -90;
    @RegisterSubModule(name = "Tower Max Pitch", parent = "Tower Pitch Range", description = "-90 is looking straight up", min = -90, max = 90)
    public static int towerMaxPitch = 0;

    // --- Visuals ---
    @RegisterSubModule(name = "Visuals")
    public static SubCategory visuals = new SubCategory();
    @RegisterSubModule(name = "Auto F5", parent = "Visuals")
    public static boolean autoF5 = true;
    @RegisterSubModule(name = "Show Target Block", parent = "Visuals")
    public static boolean showTargetBlock = true;
    @RegisterSubModule(name = "Target Block Colour", parent = "Show Target Block")
    public static Color targetBlockColour = new Color(227, 155, 248);
    @RegisterSubModule(name = "Show Previous Blocks", parent = "Visuals")
    public static boolean showPreviousBlocks = true;
    @RegisterSubModule(name = "Fade Time", parent = "Show Previous Blocks", min = 50, max = 10000, increment = 50)
    public static long showPreviousBlocksTime = 3000;
    @RegisterSubModule(name = "Item Spoof", description = "Shows the block being placed in your hand", parent = "Visuals")
    public static boolean itemSpoof = false;

    // --- Bypass & Rotation ---
    @RegisterSubModule(name = "Bypass")
    public static SubCategory bypass = new SubCategory();
    @RegisterSubModule(name = "Crouch On Edge", parent = "Bypass")
    public static boolean crouchOnEdge = false;
    @RegisterSubModule(name = "Crouch In Air", parent = "Crouch On Edge")
    public static boolean crouchInAir = false;
    @RegisterSubModule(name = "Manual Place", parent = "Bypass", description = "Manually click to place")
    public static boolean manualPlace = false;
    @RegisterSubModule(name = "Rotate", parent = "Bypass", description = "Rotate camera to target block")
    public static boolean rotate = true;
    @RegisterSubModule(name = "Rotation Mode", parent = "Rotate")
    public static BridgingMode bridgingMode = BridgingMode.Telly;
    @RegisterSubModule(name = "Only Place Best", parent = "Rotate", description = "Only places when looking at best target block")
    public static boolean onlyPlaceBest = true;
    @RegisterSubModule(name = "No Duplicate Rot", description = "Bypasses grims DuplicateRotPlace check", parent = "Rotate")
    public static boolean noDuplicateRot = true;

    public enum BridgingMode {
        God,
        Telly,
        Derp,
        Hypixel
    }

    // --- Telly Mode Settings (Specification: implementation.md & rotations.md) ---
    @RegisterSubModule(name = "Speed Telly on RMB", description = "Engage Telly speed mode while holding RMB", parent = "Rotation Mode", modeParentString = "Telly")
    public static boolean speedTellyOnRMB = false;
    @RegisterSubModule(name = "Telly Profile", description = "Rotation Profile to use for Telly mode", parent = "Rotation Mode", modeParentString = "Telly")
    public static TellyProfile tellyProfile = TellyProfile.Hpyx2;

    public enum TellyProfile {
        Hpyx2,
        Smooth,
        Eased
    }

    @RegisterSubModule(name = "Rotation Speed", description = "Rotation smoothing speed percentage", min = 50, max = 100, parent = "Rotation Mode", modeParentString = "Telly")
    public static int rotationSpeed = 85;
    @RegisterSubModule(name = "Rotation Randomness", description = "Randomness noise percentage", min = 0, max = 50, parent = "Rotation Mode", modeParentString = "Telly")
    public static int rotationRandomness = 15;
    @RegisterSubModule(name = "Rotation Tolerance", description = "Rotation tolerance in degrees", min = 5, max = 45, parent = "Rotation Mode", modeParentString = "Telly")
    public static int rotationTolerance = 15;
    @RegisterSubModule(name = "Jump Delay", description = "Jump delay post placement in ms", min = 0, max = 200, parent = "Rotation Mode", modeParentString = "Telly")
    public static int jumpDelay = 200;
    @RegisterSubModule(name = "Place CPS", description = "Target placement CPS", min = 4, max = 30, parent = "Rotation Mode", modeParentString = "Telly")
    public static int placeCPS = 16;
    @RegisterSubModule(name = "Sens Decine", description = "Mouse sensitivity tens digit", min = 0, max = 200, parent = "Rotation Mode", modeParentString = "Telly")
    public static int sensDecine = 100;
    @RegisterSubModule(name = "Sens Unita", description = "Mouse sensitivity ones digit", min = 0, max = 9, parent = "Rotation Mode", modeParentString = "Telly")
    public static int sensUnita = 0;

    @RegisterSubModule(name = "Easing Function", description = "Easing function for Eased profile", parent = "Rotation Mode", modeParentString = "Telly")
    public static EasingUtil.EasingFunctions easingFunction = EasingUtil.EasingFunctions.Ease_Out_Expo;
    @RegisterSubModule(name = "Telly Forward Ticks", description = "Ground forward ticks before jump", min = 0, max = 5, parent = "Rotation Mode", modeParentString = "Telly")
    public static int tellyForwardTicks = 1;

    // --- State Management ---
    @Getter private static boolean shouldScaffold = false;

    public static int getSlot() {
        return pickBlockSlot();
    }

    private static final ConcurrentHashMap<BlockPos, Long> previousInteractions = new ConcurrentHashMap<>();
    private static float lastPlacedDeltaX = -1;
    private static int blocksPlaced = 0;

    // Telly Core State
    private static boolean tellyEngaged = false;
    private static int tellyStartY = -1;
    private static int airborneTicks = 0;
    private static int tellyCombo = 0;
    private static int tellyForwardTicksCount = -1;
    private static boolean tellyBlockPlaced = true;
    private static boolean hasAim = false;
    private static boolean resetting = false;
    private static float aimYaw = 0;
    private static float aimPitch = 0;
    private static float yawJitter = 0;
    private static float pitchJitter = 0;
    private static int rotationTick = 0;

    // Placement State
    private static BlockPos placeAtBlock = null;
    private static EnumFacing hitSide = null;
    private static Vec3 hitVec = null;
    private static BlockPos targetHitPos = null;
    private static EnumFacing targetSide = null;
    private static boolean placeQueued = false;
    private static BlockPos lastPlaced = null;
    private static long lastPlaceTime = 0;
    private static long nextPlaceDelay = 0;

    // Gap Detection
    private static final List<BlockPos> detectedGaps = new ArrayList<>();

    // Visuals & Input
    private static int previousPerspective = 0;
    private static int previousStack = -1;
    private static boolean overridingSneak = false;
    private static BlockTarget targetBlock = null;

    // --- BedWars Block Priority Map ---
    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    static {
        BLOCK_SCORE.put("wool", 100);
        BLOCK_SCORE.put("clay", 90);
        BLOCK_SCORE.put("wood", 80);
        BLOCK_SCORE.put("planks", 80);
        BLOCK_SCORE.put("end_stone", 70);
        BLOCK_SCORE.put("obsidian", 10);
    }

    // --- Lifecycle Callbacks ---
    @Override
    protected void onEnable() {
        resetOperationalState();
    }

    @Override
    protected void onDisable() {
        disable();
    }

    private static void resetOperationalState() {
        clearAim(false);
        placeQueued = false;
        placeAtBlock = null;
        hitSide = null;
        hitVec = null;
        tellyEngaged = false;
        tellyStartY = -1;
        airborneTicks = 0;
        tellyCombo = 0;
        detectedGaps.clear();
        lastPlaceTime = 0;
        nextPlaceDelay = 0;
        rotationTick = 0;
        tellyForwardTicksCount = -1;
        tellyBlockPlaced = true;
    }

    private static void enable() {
        shouldScaffold = true;
        blocksPlaced = 0;
        resetOperationalState();

        if (autoF5) {
            previousPerspective = C.mc.gameSettings.thirdPersonView;
            C.mc.gameSettings.thirdPersonView = 1;
        }
    }

    private static void disable() {
        if (shouldScaffold) {
            shouldScaffold = false;
            targetBlock = null;
            resetOperationalState();

            if (previousStack != -1 && C.isInGame()) {
                C.p().inventory.currentItem = previousStack;
                previousStack = -1;
            }
            if (autoF5) {
                C.mc.gameSettings.thirdPersonView = previousPerspective;
            }
            overridingSneak = false;
            KeyBindingBridge.from(C.mc.gameSettings.keyBindSneak).bridge$setDown(Keyboard.isKeyDown(C.mc.gameSettings.keyBindSneak.getKeyCode()));
        }
    }

    // --- Event Subscriptions ---
    @SubscribeEvent
    public static void onKeyInput(MovementInputEvent event) {
        if (!shouldScaffold || C.p() == null || C.p().capabilities.isFlying) return;

        long now = System.currentTimeMillis();
        boolean airborne = !C.p().onGround;

        // Suppress jump if recently placed and still in jumpDelay window
        if (airborne && now - lastPlaceTime < jumpDelay) {
            event.movementInput.jump = false;
        }

        if (shouldTelly()) {
            boolean moving = Math.abs(C.p().motionX) > 0.05 || Math.abs(C.p().motionZ) > 0.05
                    || Keyboard.isKeyDown(C.mc.gameSettings.keyBindForward.getKeyCode())
                    || Keyboard.isKeyDown(C.mc.gameSettings.keyBindBack.getKeyCode())
                    || Keyboard.isKeyDown(C.mc.gameSettings.keyBindLeft.getKeyCode())
                    || Keyboard.isKeyDown(C.mc.gameSettings.keyBindRight.getKeyCode());

            if (C.p().onGround && moving) {
                tellyForwardTicksCount++;
                if (tellyForwardTicks == 0 || tellyForwardTicksCount >= tellyForwardTicks) {
                    event.movementInput.jump = true;
                }
            } else if (!C.p().onGround) {
                tellyForwardTicksCount = -1;
            }
        } else {
            event.movementInput.jump |= (towerMode == TowerMode.Legit && shouldTower());
        }
    }

    @SubscribeEvent(priority = 3000)
    public static void onRotationEvent(RotationEvent event) {
        if (C.p() == null || C.w() == null) return;

        if (!InventoryUtil.isValidBlock() && blocksOnly || biggestBlockSlot() == -1 || !shouldScaffold()) {
            disable();
            return;
        }

        if (!shouldScaffold) enable();

        // Hotbar selection
        int chosenSlot = pickBlockSlot();
        if (chosenSlot != -1 && chosenSlot != C.p().inventory.currentItem) {
            if (previousStack == -1) previousStack = C.p().inventory.currentItem;
            C.p().inventory.currentItem = chosenSlot;
        }

        // Check screen/GUI state
        if (C.mc.currentScreen != null) {
            clearAim(false);
            return;
        }

        if (shouldTelly()) {
            runTellyTick(event);
        } else {
            runStandardRotation(event);
        }
    }

    @SubscribeEvent
    public static void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!shouldScaffold() || C.p() == null || C.w() == null) return;

        if (InventoryUtil.isValidBlock() && crouchDownOnly && uncrouchAuto) {
            KeyBindingBridge.from(C.mc.gameSettings.keyBindSneak).bridge$setDown(false);
        }

        if (!shouldTelly() && (!shouldPlaceBlock() || !InventoryUtil.isValidBlock())) {
            if (overridingSneak && (C.p().onGround || !crouchInAir)) {
                KeyBindingBridge.from(C.mc.gameSettings.keyBindSneak).bridge$setDown(Keyboard.isKeyDown(C.mc.gameSettings.keyBindSneak.getKeyCode()));
                overridingSneak = false;
            }
            return;
        }

        if (shouldTower() && towerMovement()) setShouldTower();

        if (crouchOnEdge && (C.p().onGround || crouchInAir)) {
            KeyBindingBridge.from(C.mc.gameSettings.keyBindSneak).bridge$setDown(true);
            overridingSneak = true;
        }

        // Placement Execution Pipeline (Section 17 & 18)
        if (placeQueued) {
            executeQueuedPlacement();
        }
    }

    @SubscribeEvent
    public static void onRightClick(ClickMouseEvent.Right event) {
        if (!shouldScaffold() || !InventoryUtil.isValidBlock()) return;

        if (speedTellyOnRMB || WorldUtil.isOverAir()) {
            event.setCancelled(true);
        }
    }

    // --- Telly Core State Machine ---
    private static void runTellyTick(RotationEvent event) {
        boolean onGround = C.p().onGround;

        if (onGround) {
            airborneTicks = 0;
            // When on ground, if we have a locked TellyStartY keep it, or initialize when over air / moving
            if (WorldUtil.isOverAir()) {
                if (tellyStartY == -1) {
                    tellyStartY = MathHelper.floor_double(C.p().posY) - 1;
                }
                tellyEngaged = true;
            } else {
                tellyEngaged = false;
                tellyStartY = -1;
                if (hasAim && !placeQueued) clearAim(true);
            }
        } else {
            airborneTicks++;
            if (tellyStartY == -1) {
                tellyStartY = MathHelper.floor_double(C.p().posY) - 1;
            }
            tellyEngaged = true;
        }

        if (resetting) {
            // Fast snapback convergence
            RotationUtil.Rotation currentRot = PlayerUtil.lastRotation();
            RotationUtil.Rotation targetRot = new RotationUtil.Rotation(C.p().rotationPitch, C.p().rotationYaw);
            RotationUtil.Rotation smoothed = getRotationsSmoothed(currentRot, targetRot, true);
            event.rotation = smoothed;

            float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(smoothed.yaw - targetRot.yaw));
            float pitchDiff = Math.abs(smoothed.pitch - targetRot.pitch);
            if (yawDiff < 3.0F && pitchDiff < 3.0F) {
                resetting = false;
                hasAim = false;
            }
            return;
        }

        if (!tellyEngaged || tellyStartY == -1) return;

        // Gap scanning
        checkForGaps();

        // Calculate placement target and aim
        AimResult aim = clutchAim();
        if (aim == null) {
            if (hasAim && !placeQueued) clearAim(true);
            return;
        }

        if (!hasAim || Math.abs(aim.yaw - aimYaw) > 15 || Math.abs(aim.pitch - aimPitch) > 15) {
            rotationTick = 0;
            generateJitter();
        }

        hasAim = true;
        aimYaw = aim.yaw;
        aimPitch = aim.pitch;
        targetHitPos = aim.hitPos;
        targetSide = aim.hitSide;

        // Apply rotation based on chosen profile
        RotationUtil.Rotation currentRot = PlayerUtil.lastRotation();
        RotationUtil.Rotation targetRot = new RotationUtil.Rotation(aimPitch + pitchJitter, aimYaw + yawJitter);
        RotationUtil.Rotation nextRot;

        switch (tellyProfile) {
            case Hpyx2:
                nextRot = getHpyx2Rotation(currentRot, targetRot);
                break;
            case Eased:
                double lerp = Math.min(1.0, (rotationTick + 1.0) / 3.0);
                nextRot = RotationUtil.getEasedRotation(currentRot, targetRot, easingFunction, lerp);
                break;
            case Smooth:
            default:
                nextRot = getRotationsSmoothed(currentRot, targetRot, false);
                break;
        }

        rotationTick++;
        event.rotation = nextRot;

        // Raycast Validation (Section 16)
        validatePlacementRaycast(nextRot);
    }

    // --- Staged Hpyx2 Rotation Profile (rotations.md & fix.md) ---
    private static RotationUtil.Rotation getHpyx2Rotation(RotationUtil.Rotation from, RotationUtil.Rotation to) {
        float yawSpeed;
        float pitchSpeed;

        if (rotationTick < 2) {
            // Phase 1 — Fast Initial Approach
            yawSpeed = 55.0F + (float) Math.random() * 20.0F;
            pitchSpeed = 30.0F + (float) Math.random() * 15.0F;
        } else {
            // Phase 2 — Snappy Convergence
            yawSpeed = 90.0F + (float) Math.random() * 25.0F;
            pitchSpeed = 50.0F + (float) Math.random() * 20.0F;
        }

        float yawDelta = MathHelper.wrapAngleTo180_float(to.yaw - from.yaw);
        float pitchDelta = MathHelper.clamp_float(to.pitch - from.pitch, -90.0F, 90.0F);

        float clampedYaw = MathHelper.clamp_float(yawDelta, -yawSpeed, yawSpeed);
        float clampedPitch = MathHelper.clamp_float(pitchDelta, -pitchSpeed, pitchSpeed);

        RotationUtil.Rotation stepRot = new RotationUtil.Rotation(
                MathHelper.clamp_float(from.pitch + clampedPitch, -90.0F, 90.0F),
                from.yaw + clampedYaw
        );

        return RotationUtil.applyGcd(from, stepRot);
    }

    // --- Standard Smoothing & Snapback (implementation.md Section 13-15 & fix.md) ---
    private static RotationUtil.Rotation getRotationsSmoothed(RotationUtil.Rotation from, RotationUtil.Rotation to, boolean snapback) {
        float speed = snapback ? 100.0F : (float) rotationSpeed;
        float maxStep = snapback ? 75.0F : (30.0F + speed * 0.55F);
        float smoothness = snapback ? 1.0F : Math.max(0.7F, speed / 100.0F);

        float yawDiff = MathHelper.wrapAngleTo180_float(to.yaw - from.yaw);
        float pitchDiff = MathHelper.clamp_float(to.pitch - from.pitch, -90.0F, 90.0F);

        float yawStep = MathHelper.clamp_float(yawDiff * smoothness, -maxStep, maxStep);
        float pitchStep = MathHelper.clamp_float(pitchDiff * smoothness, -maxStep, maxStep);

        RotationUtil.Rotation raw = new RotationUtil.Rotation(
                MathHelper.clamp_float(from.pitch + pitchStep, -90.0F, 90.0F),
                from.yaw + yawStep
        );

        return RotationUtil.applyGcd(from, raw);
    }

    // --- Sensitivity & GCD Calculation (Section 14) ---
    private static double getRotationGCD() {
        double totalSens = (sensDecine + sensUnita) / 100.0;
        double fGcd = totalSens * 0.6 + 0.2;
        return (fGcd * fGcd * fGcd) * 1.2;
    }

    private static void generateJitter() {
        double randFactor = rotationRandomness / 100.0;
        yawJitter = (float) (getGaussianNoise() * randFactor * 1.2);
        pitchJitter = (float) (getGaussianNoise() * randFactor * 0.8);
    }

    private static double getGaussianNoise() {
        Random rand = new Random();
        return rand.nextGaussian();
    }

    // --- Raycast Placement Validation (Section 16) ---
    private static void validatePlacementRaycast(RotationUtil.Rotation rotation) {
        MovingObjectPosition hit = WorldUtil.rayTrace(blockReach, rotation);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        if (hit.sideHit == EnumFacing.DOWN) return;

        BlockPos hitBlockPos = hit.getBlockPos();
        BlockPos destinationPos = hitBlockPos.offset(hit.sideHit);

        if (!isValidPlaceTarget(destinationPos, hitBlockPos, hit.sideHit)) return;

        // Convergence check: angle to candidate target
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotation.yaw - aimYaw));
        float pitchDiff = Math.abs(rotation.pitch - aimPitch);
        boolean angleWithinTolerance = yawDiff <= rotationTolerance && pitchDiff <= rotationTolerance;
        boolean matchesTarget = targetHitPos != null && hitBlockPos.equals(targetHitPos) && hit.sideHit == targetSide;

        if (angleWithinTolerance || matchesTarget) {
            placeAtBlock = hitBlockPos;
            hitSide = hit.sideHit;
            hitVec = hit.hitVec;
            placeQueued = true;
            targetBlock = new BlockTarget(hitBlockPos, hit.sideHit);
        }
    }

    // --- Placement Execution (Section 17-18 & Persistence) ---
    private static void executeQueuedPlacement() {
        if (!placeQueued || placeAtBlock == null || hitSide == null || hitVec == null) return;

        long now = System.currentTimeMillis();
        // Do NOT clear placeQueued while waiting for place delay
        if (now - lastPlaceTime < nextPlaceDelay) return;

        ItemStack held = C.p().getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            placeQueued = false;
            return;
        }

        if (C.mc.playerController.onPlayerRightClick(C.p(), C.w(), held, placeAtBlock, hitSide, hitVec)) {
            PlayerUtil.swingHand();
            if (InventoryUtil.isSlotEmpty(C.p().inventory.currentItem)) {
                C.p().inventory.removeStackFromSlot(C.p().inventory.currentItem);
            }

            blocksPlaced++;
            tellyCombo++;
            lastPlaced = placeAtBlock.offset(hitSide);
            previousInteractions.put(lastPlaced, now);
            detectedGaps.remove(lastPlaced);

            lastPlaceTime = now;
            nextPlaceDelay = rollPlaceDelay(true);

            // Successfully placed: clear queue & active aim to immediately acquire next target
            placeQueued = false;
            hasAim = false;
            targetHitPos = null;
            targetSide = null;
            placeAtBlock = null;
            hitSide = null;
            hitVec = null;
            tellyBlockPlaced = true;
        } else {
            nextPlaceDelay = rollPlaceDelay(false);
        }
    }

    private static long rollPlaceDelay(boolean success) {
        double baseDelay = 1000.0 / Math.max(1, placeCPS);
        if (success) {
            double multiplier = 0.75 + Math.random() * (1.35 - 0.75);
            long delay = (long) (baseDelay * multiplier);
            if (Math.random() < 0.08) delay += (long) (Math.random() * 40.0);
            return Math.max(25L, delay);
        } else {
            double multiplier = 0.25 + Math.random() * 0.25;
            return Math.max(15L, (long) (baseDelay * multiplier));
        }
    }

    private static void clearAim(boolean snapback) {
        hasAim = false;
        placeQueued = false;
        placeAtBlock = null;
        hitSide = null;
        hitVec = null;
        targetHitPos = null;
        targetSide = null;
        if (snapback) {
            resetting = true;
        } else {
            resetting = false;
        }
    }

    // --- Gap Detection (Section 8) ---
    private static void checkForGaps() {
        detectedGaps.clear();
        int playerFeetX = MathHelper.floor_double(C.p().posX);
        int playerFeetZ = MathHelper.floor_double(C.p().posZ);
        int targetY = tellyStartY;

        for (int x = playerFeetX - 5; x <= playerFeetX + 5; x++) {
            for (int z = playerFeetZ - 5; z <= playerFeetZ + 5; z++) {
                BlockPos pos = new BlockPos(x, targetY, z);
                if (canPlaceThrough(pos)) continue;

                if (lastPlaced != null && (Math.abs(lastPlaced.getX() - x) <= 1 && Math.abs(lastPlaced.getZ() - z) <= 1)) {
                    continue;
                }

                for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                    BlockPos neighbor = pos.offset(facing);
                    if (canPlaceThrough(neighbor)) {
                        int dx = Math.abs(neighbor.getX() - playerFeetX);
                        int dz = Math.abs(neighbor.getZ() - playerFeetZ);
                        if ((dx >= 3 && dx <= 5) || (dz >= 3 && dz <= 5)) {
                            detectedGaps.add(neighbor);
                        }
                    }
                }
            }
        }
    }

    // --- Target Selection & Surface Sampling (Section 9, 11, 12) ---
    private static AimResult clutchAim() {
        Vec3 eyePos = C.p().getPositionEyes(1.0F);

        // Motion vector prediction for forward placement continuation
        double velX = C.p().motionX;
        double velZ = C.p().motionZ;
        double speed = Math.sqrt(velX * velX + velZ * velZ);

        double predX = C.p().posX + (speed > 0.05 ? velX * 2.0 : 0.0);
        double predZ = C.p().posZ + (speed > 0.05 ? velZ * 2.0 : 0.0);

        // Special Telly Aim: Try predicted cell first
        BlockPos predictedCell = new BlockPos(MathHelper.floor_double(predX), tellyStartY, MathHelper.floor_double(predZ));
        if (canPlaceThrough(predictedCell)) {
            AimResult directAim = getBestRotationsToFillCell(predictedCell);
            if (directAim != null) return directAim;
        }

        // Secondary: Try cell under eye position
        BlockPos eyeCell = new BlockPos(MathHelper.floor_double(eyePos.xCoord), tellyStartY, MathHelper.floor_double(eyePos.zCoord));
        if (!eyeCell.equals(predictedCell) && canPlaceThrough(eyeCell)) {
            AimResult directAim = getBestRotationsToFillCell(eyeCell);
            if (directAim != null) return directAim;
        }

        // Candidate search across nearby cells
        List<BlockCandidate> candidates = new ArrayList<>();
        int feetX = MathHelper.floor_double(C.p().posX);
        int feetZ = MathHelper.floor_double(C.p().posZ);

        for (int x = feetX - 5; x <= feetX + 5; x++) {
            for (int z = feetZ - 5; z <= feetZ + 5; z++) {
                BlockPos pos = new BlockPos(x, tellyStartY, z);
                if (canPlaceThrough(pos)) continue;

                AxisAlignedBB aabb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                double dist = distPointToAABB(new Vec3(predX, C.p().posY, predZ), aabb);
                if (pos.equals(lastPlaced)) dist *= 0.90;

                candidates.add(new BlockCandidate(pos, dist));
            }
        }

        candidates.sort(Comparator.comparingDouble(a -> a.distance));

        for (BlockCandidate candidate : candidates) {
            AimResult result = getBestRotationsToBlock(candidate.pos);
            if (result != null) return result;
        }

        return null;
    }

    private static AimResult getBestRotationsToFillCell(BlockPos cell) {
        Vec3 eyePos = C.p().getPositionEyes(1.0F);
        // Find best direction based on player motion or backwards direction
        float motionYaw = (float) Math.toDegrees(Math.atan2(C.p().motionZ, C.p().motionX)) - 90.0F;
        float refYaw = (Math.abs(C.p().motionX) > 0.05 || Math.abs(C.p().motionZ) > 0.05) ? motionYaw - 180.0F : C.p().rotationYaw - 180.0F;

        AimResult best = null;
        float bestCost = Float.MAX_VALUE;

        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos adjacent = cell.offset(facing);
            if (!canPlaceThrough(adjacent)) {
                EnumFacing hitFace = facing.getOpposite();
                AimResult result = getBestRotationsToFace(adjacent, hitFace);
                if (result != null) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(result.yaw - refYaw));
                    if (diff < bestCost) {
                        bestCost = diff;
                        best = result;
                    }
                }
            }
        }
        return best;
    }

    private static AimResult getBestRotationsToBlock(BlockPos pos) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (facing == EnumFacing.DOWN) continue;
            BlockPos dest = pos.offset(facing);
            if (canPlaceThrough(dest)) {
                AimResult result = getBestRotationsToFace(pos, facing);
                if (result != null) return result;
            }
        }
        return null;
    }

    // Sampling face with 0.05 inset, 0.2 step, jitter
    private static AimResult getBestRotationsToFace(BlockPos blockPos, EnumFacing facing) {
        Vec3 eyePos = C.p().getPositionEyes(1.0F);
        double inset = 0.05;
        double step = 0.2;
        double jitter = step * 0.1;

        List<RotationCandidate> candidates = new ArrayList<>();

        double minU = inset;
        double maxU = 1.0 - inset;
        double minV = inset;
        double maxV = 1.0 - inset;

        for (double u = minU; u <= maxU; u += step) {
            for (double v = minV; v <= maxV; v += step) {
                double ju = u + (Math.random() - 0.5) * jitter;
                double jv = v + (Math.random() - 0.5) * jitter;

                Vec3 targetPoint = getPointOnFace(blockPos, facing, ju, jv);
                float[] angles = getRotationsWrapped(eyePos, targetPoint);
                float cost = (float) eyePos.squareDistanceTo(targetPoint);

                candidates.add(new RotationCandidate(targetPoint, angles[0], angles[1], cost));
            }
        }

        candidates.sort(Comparator.comparingDouble(a -> a.cost));

        for (RotationCandidate rc : candidates) {
            RotationUtil.Rotation testRot = new RotationUtil.Rotation(rc.pitch, rc.yaw);
            MovingObjectPosition hit = WorldUtil.rayTrace(blockReach, testRot);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (hit.sideHit != EnumFacing.DOWN && hit.getBlockPos().equals(blockPos) && hit.sideHit == facing) {
                    return new AimResult(hit.getBlockPos(), hit.sideHit, rc.yaw, rc.pitch);
                }
            }
        }

        return null;
    }

    private static Vec3 getPointOnFace(BlockPos pos, EnumFacing facing, double u, double v) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        switch (facing) {
            case UP:
                return new Vec3(x + u, y + 1.0, z + v);
            case DOWN:
                return new Vec3(x + u, y, z + v);
            case NORTH:
                return new Vec3(x + u, y + v, z);
            case SOUTH:
                return new Vec3(x + u, y + v, z + 1.0);
            case WEST:
                return new Vec3(x, y + v, z + u);
            case EAST:
                return new Vec3(x + 1.0, y + v, z + u);
            default:
                return new Vec3(x + 0.5, y + 0.5, z + 0.5);
        }
    }

    // --- Rotation Geometry Math (Section 10) ---
    public static float[] getRotationsWrapped(Vec3 eyePos, Vec3 target) {
        double dx = target.xCoord - eyePos.xCoord;
        double dy = target.yCoord - eyePos.yCoord;
        double dz = target.zCoord - eyePos.zCoord;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        return new float[]{
                MathHelper.wrapAngleTo180_float(yaw),
                MathHelper.clamp_float(pitch, -90.0F, 90.0F)
        };
    }

    // --- Standard Non-Telly Bridging ---
    private static void runStandardRotation(RotationEvent event) {
        Vec3 pos = C.p().getPositionVector();
        if (!shouldPlaceBlock()) {
            Vec3 pred = getPredictedNextPosition();
            if (pred != null) pos = pred;
        }

        targetBlock = getBestTargetBlock(pos);
        if (targetBlock == null) return;

        MovingObjectPosition ray = WorldUtil.rayTrace(blockReach, pos, PlayerUtil.lastRotation());
        BlockPos currentBlock = (ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) ? ray.getBlockPos().offset(ray.sideHit) : null;
        BlockPos expectedBlock = targetBlock.pos.offset(targetBlock.direction);

        if (currentBlock == null || !currentBlock.equals(expectedBlock)) {
            float closestPitch = 91;
            float closestYaw = 181;
            boolean found = false;

            for (float yaw = -closestYaw + 1; yaw <= Math.abs(closestYaw); yaw++) {
                for (float pitch = 90; pitch >= 0; pitch--) {
                    RotationUtil.Rotation gcded = RotationUtil.applyGcd(new RotationUtil.Rotation(pitch, PlayerUtil.lastRotation().yaw + yaw));
                    float yawChange = gcded.yaw - PlayerUtil.lastRotation().yaw;

                    float deltaX = Math.abs(yawChange);
                    if (deltaX > 2 && noDuplicateRot) {
                        float xDiff = Math.abs(deltaX - lastPlacedDeltaX);
                        if (xDiff < 0.0001) continue;
                    }

                    MovingObjectPosition raycast = WorldUtil.rayTrace(blockReach, pos, gcded);
                    if (raycast == null || raycast.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                    BlockPos raycastBlock = raycast.getBlockPos().offset(raycast.sideHit);

                    if (raycastBlock.equals(expectedBlock)) {
                        float bestChange = Math.abs(PlayerUtil.lastRotation().pitch - closestPitch) + Math.abs(closestYaw);
                        float curChange = Math.abs(PlayerUtil.lastRotation().pitch - gcded.pitch) + Math.abs(yawChange);
                        if (curChange < bestChange) {
                            closestYaw = yawChange;
                            closestPitch = gcded.pitch;
                            found = true;
                        }
                    }
                }
            }

            if (found) {
                RotationUtil.Rotation bestRotation = new RotationUtil.Rotation(closestPitch, PlayerUtil.lastRotation().yaw + closestYaw);
                if (bridgingMode == BridgingMode.Hypixel) {
                    float yawSpeed = 75.0F + (float) Math.random() * 15.0F;
                    float pitchSpeed = 35.0F + (float) Math.random() * 10.0F;
                    float yawDelta = MathHelper.wrapAngleTo180_float(bestRotation.yaw - PlayerUtil.lastRotation().yaw);
                    float pitchDelta = MathHelper.clamp_float(bestRotation.pitch - PlayerUtil.lastRotation().pitch, -90.0F, 90.0F);
                    event.rotation = new RotationUtil.Rotation(
                            MathHelper.clamp_float(PlayerUtil.lastRotation().pitch + MathHelper.clamp_float(pitchDelta, -pitchSpeed, pitchSpeed), -90.0F, 90.0F),
                            PlayerUtil.lastRotation().yaw + MathHelper.clamp_float(yawDelta, -yawSpeed, yawSpeed)
                    );
                } else {
                    event.rotation = bestRotation;
                }
                if (!manualPlace) {
                    placeAtBlock = targetBlock.pos;
                    hitSide = targetBlock.direction;
                    hitVec = new Vec3(targetBlock.pos.getX() + 0.5, targetBlock.pos.getY() + 0.5, targetBlock.pos.getZ() + 0.5);
                    placeQueued = true;
                }
                return;
            }
        }

        if (bridgingMode != BridgingMode.Derp) {
            event.rotation = PlayerUtil.lastRotation();
        }
    }

    // --- Block & Cell Validation Utilities ---
    public static boolean canPlaceThrough(BlockPos pos) {
        if (C.w() == null) return false;
        Block block = C.w().getBlockState(pos).getBlock();
        return block instanceof BlockAir || block instanceof BlockLiquid || block instanceof BlockFire;
    }

    private static boolean isValidPlaceTarget(BlockPos destCell, BlockPos hitBlockPos, EnumFacing side) {
        if (!canPlaceThrough(destCell)) return false;
        if (destCell.getY() > C.p().posY) return false;
        return true;
    }

    private static double distPointToAABB(Vec3 point, AxisAlignedBB aabb) {
        double cx = MathHelper.clamp_double(point.xCoord, aabb.minX, aabb.maxX);
        double cy = MathHelper.clamp_double(point.yCoord, aabb.minY, aabb.maxY);
        double cz = MathHelper.clamp_double(point.zCoord, aabb.minZ, aabb.maxZ);
        return point.distanceTo(new Vec3(cx, cy, cz));
    }

    // --- Hotbar & BedWars Inventory Selection (Section 19) ---
    private static int pickBlockSlot() {
        if (isPlayingBedwars()) {
            int bestSlot = -1;
            int bestScore = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = C.p().inventory.getStackInSlot(i);
                if (isBlockSlot(stack)) {
                    int score = getBlockScore(stack);
                    if (stack.stackSize > 5) score += 1000;
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = i;
                    }
                }
            }
            if (bestSlot != -1) return bestSlot;
        }

        int current = C.p().inventory.currentItem;
        ItemStack currentStack = C.p().inventory.getStackInSlot(current);
        if (isBlockSlot(currentStack) && currentStack.stackSize > 5) return current;

        return biggestBlockSlot();
    }

    private static boolean isPlayingBedwars() {
        if (C.mc.getCurrentServerData() != null && C.mc.getCurrentServerData().serverIP.toLowerCase().contains("hypixel")) {
            Scoreboard sb = C.w().getScoreboard();
            ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
            if (obj != null && obj.getDisplayName().toLowerCase().contains("bed")) return true;
        }
        return false;
    }

    private static int getBlockScore(ItemStack stack) {
        String name = stack.getDisplayName().toLowerCase();
        for (Map.Entry<String, Integer> entry : BLOCK_SCORE.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }
        return 1;
    }

    private static boolean isBlockSlot(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBlock && InventoryUtil.isSolidBlock(((ItemBlock) stack.getItem()).getBlock());
    }

    private static int biggestBlockSlot() {
        int bestSlot = -1;
        int largest = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = C.p().inventory.getStackInSlot(i);
            if (isBlockSlot(stack) && stack.stackSize > largest) {
                largest = stack.stackSize;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    // --- Tower & Movement Helpers ---
    private static boolean shouldTower = false;
    private static boolean shouldTower() {
        if (towerMode == TowerMode.None) return false;
        if (C.p().onGround || MovementUtil.airTicks == 1) setShouldTower();
        return shouldTower;
    }

    private static void setShouldTower() {
        shouldTower = (!onlyOffGround || !C.p().onGround)
                && (!onlyIfSpaceDown || C.mc.gameSettings.keyBindJump.isKeyDown())
                && (!onlyTowerLookingUp || (C.p().rotationPitch <= towerMaxPitch && C.p().rotationPitch >= towerMinPitch));
    }

    private static boolean towerMovement() {
        switch (towerMode) {
            case Legit:
                return true;
            case Vanilla:
                int playerYto2Decimals = (int) ((C.p().posY % 1) * 100);
                switch (playerYto2Decimals) {
                    case 0:
                        C.p().motionY = 0.42F;
                        break;
                    case 41:
                        C.p().motionY = 0.33F;
                        break;
                    case 75:
                        C.p().motionY = 1.0 - (C.p().posY % 1);
                        return true;
                }
        }
        return false;
    }

    private static BlockTarget getBestTargetBlock(Vec3 position) {
        int playerY = MathHelper.floor_double(C.p().posY);
        int targetY = playerY;
        BlockPos blockPosition = new BlockPos(position.xCoord, targetY, position.zCoord);
        BlockPos point1 = blockPosition.add(-blockReach, -blockReach, -blockReach);
        BlockPos point2 = blockPosition.add(blockReach, -1, blockReach);
        Iterator<BlockPos> blocksInRange = BlockPos.getAllInBox(point1, point2).iterator();

        double bestDistance = Integer.MAX_VALUE;
        BlockTarget bestBlock = null;

        while (blocksInRange.hasNext()) {
            BlockPos blockPos = blocksInRange.next();
            Block currentBlock = C.w().getBlockState(blockPos).getBlock();

            if (currentBlock == null || InventoryUtil.isBlockInteractable(currentBlock) || !InventoryUtil.isSolidBlock(currentBlock)) continue;

            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos blockPosOffset = blockPos.offset(facing);
                if (facing == EnumFacing.DOWN) continue;
                if (InventoryUtil.isSolidBlock(C.w().getBlockState(blockPosOffset).getBlock())) continue;
                if (blockPosOffset.getY() + 1 > C.p().posY) continue;

                Vec3 offsetBlockCentre = new Vec3(blockPosOffset.getX() + 0.5, blockPosOffset.getY() + 0.5, blockPosOffset.getZ() + 0.5);
                double distance = position.distanceTo(offsetBlockCentre);

                if (distance > bestDistance) continue;

                bestDistance = distance;
                bestBlock = new BlockTarget(blockPos, facing);
            }
        }

        if (bestDistance > blockReach) return null;
        return bestBlock;
    }

    private static boolean shouldScaffold() {
        return (!crouchDownOnly || Keyboard.isKeyDown(C.mc.gameSettings.keyBindSneak.getKeyCode()))
                && (!rightClickOnly || C.mc.gameSettings.keyBindUseItem.isKeyDown())
                && (!pitchRange || (C.p().rotationPitch <= maxPitch && C.p().rotationPitch >= minPitch))
                && (!movingBackwards || Keyboard.isKeyDown(C.mc.gameSettings.keyBindBack.getKeyCode()));
    }

    private static boolean shouldTelly() {
        return rotate && bridgingMode == BridgingMode.Telly;
    }

    private static boolean shouldPlaceBlock() {
        return WorldUtil.isOverAir() && (C.p().onGround || WorldUtil.isOverAir(C.p().getPositionVector().subtract(0, 1, 0)));
    }

    private static Vec3 getPredictedNextPosition() {
        Vec3 pos = C.p().getPositionVector();
        double velocityX = C.p().posX - C.p().prevPosX;
        double velocityZ = C.p().posZ - C.p().prevPosZ;

        for (int i = 1; i <= 20; i++) {
            pos = pos.add(new Vec3(velocityX, 0, velocityZ));
            if (WorldUtil.isOverAir(pos)) return pos;
        }
        return null;
    }

    // --- Render ---
    @SubscribeEvent
    public static void onRenderWorldEvent(RenderWorldEvent event) {
        if (showPreviousBlocks) {
            previousInteractions.forEach((blockPos, time) -> {
                if (System.currentTimeMillis() - time > showPreviousBlocksTime) {
                    previousInteractions.remove(blockPos);
                    return;
                }

                double animationValue = (double) (System.currentTimeMillis() - time) / showPreviousBlocksTime;
                Color color = RenderUtil.getColorsFade(time / 20d, ThemeModule.getThemeColours(), 0.2f);

                Render3dUtil.draw3dBox(
                        blockPos.getX(),
                        blockPos.getY(),
                        blockPos.getZ(),
                        1,
                        1,
                        1,
                        RenderUtil.setOpacity(color, 0.5 * (1 - animationValue)),
                        event.partialTicks,
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.DOWN)),
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.UP)),
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.NORTH)),
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.SOUTH)),
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.WEST)),
                        !previousInteractions.containsKey(blockPos.offset(EnumFacing.EAST)),
                        false
                );
            });
        }

        if (showTargetBlock && targetBlock != null) {
            BlockPos blockPos = targetBlock.pos.offset(targetBlock.direction);
            Render3dUtil.draw3dBox(
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    1,
                    1,
                    1,
                    targetBlockColour,
                    event.partialTicks
            );
        }
    }

    @Override
    public String arrayListExtraInfo() {
        return rotate ? bridgingMode.name() : "Safewalk";
    }

    // --- Supporting Data Structures (Section 28) ---
    @AllArgsConstructor
    private static class BlockTarget {
        public BlockPos pos;
        public EnumFacing direction;
    }

    @AllArgsConstructor
    private static class BlockCandidate {
        public BlockPos pos;
        public double distance;
    }

    @AllArgsConstructor
    private static class RotationCandidate {
        public Vec3 hitPoint;
        public float yaw;
        public float pitch;
        public float cost;
    }

    @AllArgsConstructor
    private static class AimResult {
        public BlockPos hitPos;
        public EnumFacing hitSide;
        public float yaw;
        public float pitch;
    }
}