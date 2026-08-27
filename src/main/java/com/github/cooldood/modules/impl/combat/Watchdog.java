package com.github.cooldood.modules.impl.combat;

import com.github.cooldood.bridge.net.minecraft.client.entity.AbstractClientPlayerBridge;
import com.github.cooldood.bridge.net.minecraft.client.settings.KeyBindingBridge;
import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.ClientTickEvent;
import com.github.cooldood.events.impl.PlayerUpdateEvent;
import com.github.cooldood.events.impl.RespawnEvent;
import com.github.cooldood.modules.*;
import com.github.cooldood.modules.impl.player.Fucker;
import com.github.cooldood.utils.client.C;
import com.github.cooldood.utils.client.MathUtil;
import com.github.cooldood.utils.minecraft.MovementUtil;
import com.github.cooldood.utils.minecraft.PacketUtil;
import com.github.cooldood.utils.minecraft.PlayerUtil;
import com.github.cooldood.utils.minecraft.TargetUtil;
import lombok.Getter;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.Comparator;
import java.util.List;

@RegisterModule(
        name = "Watchdog",
        description = "Legitimate combat AutoBlock replicating a top-tier PvP player's blockhitting timings.",
        category = Category.COMBAT
)
public class Watchdog extends Module {

    // ─── Range Setting (Target reach boundary) ─────────────────────────────────
    @RegisterSubModule(name = "Range", min = 1.0, max = 6.0)
    public static double range = 3.0;

    // ─── State Management ─────────────────────────────────────────────────────
    @Getter
    private static boolean blocking = false;
    @Getter
    private static boolean serverBlocking = false;
    private static EntityLivingBase currentTarget = null;

    private static int attackCounter = 0;
    private static long nextAttackTime = 0;
    private static long blockReleaseTime = 0;

    public static boolean isModuleBlocking() {
        return ModuleManager.isEnabled(Watchdog.class) && blocking;
    }

    @SubscribeEvent(priority = 997)
    public static void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!C.isInGame() || C.p().isDead) {
            stopBlocking();
            return;
        }

        if (Fucker.getCurrentTarget() != null && Fucker.noAutoblock) {
            stopBlocking();
            return;
        }

        if (!isHoldingSword()) {
            stopBlocking();
            return;
        }

        currentTarget = findTarget();
        if (currentTarget == null) {
            stopBlocking();
            return;
        }

        tickLegitCombat(currentTarget);
    }

    /**
     * Replicates a highly skilled legitimate 1.8.9 PvP blockhitting pattern:
     * - Attacks at a realistic 5-10 CPS curve adapted to network latency.
     * - When the enemy takes damage (hurtResistantTime > 10) or trades hits with the player,
     *   the player quickly holds sword-block (1 C08 block packet) to reduce incoming trade damage by 50%.
     * - Once the enemy's damage-immunity window expires (hurtResistantTime <= 10), releases block smoothly
     *   and delivers well-timed, accurate swings with natural human variance (~80-140ms intervals).
     */
    private static void tickLegitCombat(EntityLivingBase target) {
        long now = System.currentTimeMillis();

        boolean enemyImmune = target.hurtResistantTime > 10 || target.hurtTime > 0;
        boolean playerTakingDamage = C.p().hurtTime > 0;

        if (enemyImmune || playerTakingDamage) {
            // Defensive sword-block: enemy is currently immune to damage or trading back
            sendSingleBlockPacket();
            blockReleaseTime = now + (long) MathUtil.getRandomInRange(60, 110);
        } else {
            // Enemy is damageable: release block naturally and attack
            if (blocking) {
                if (now >= blockReleaseTime) {
                    stopBlocking();
                }
            }

            if (!blocking && now >= nextAttackTime) {
                // Execute standard legitimate attack
                PlayerUtil.attack(target);
                attackCounter++;

                // Calculate humanized inter-attack interval (5-10 CPS, ~100-180ms with ping adaptation)
                double adaptiveCps = calculateAdaptiveCps();
                long baseDelay = (long) (1000.0 / adaptiveCps);
                long humanJitter = (long) MathUtil.getRandomInRange(-15, 25);
                nextAttackTime = now + Math.max(85, baseDelay + humanJitter);

                // Skilled players tap swordblock every 2-3 hits or immediately upon seeing the hit register
                if (attackCounter % 2 == 0) {
                    sendSingleBlockPacket();
                    blockReleaseTime = now + (long) MathUtil.getRandomInRange(50, 90);
                }
            }
        }
    }

    /**
     * Adapts realistic attack rhythm (5-10 attacks/sec) dynamically according to network ping.
     */
    private static double calculateAdaptiveCps() {
        int ping = 50;
        if (C.p() != null) {
            try {
                NetworkPlayerInfo info = AbstractClientPlayerBridge.from(C.p()).bridge$getPlayerInfo();
                if (info != null) {
                    ping = info.getResponseTime();
                }
            } catch (Exception ignored) {}
        }

        if (ping <= 50) return MathUtil.getRandomInRange(8.5, 10.0);
        if (ping >= 200) return MathUtil.getRandomInRange(5.0, 6.5);

        double factor = (200.0 - ping) / 150.0;
        return 5.0 + (factor * 4.5);
    }

    /**
     * Sends 1 sword block packet (C08PacketPlayerBlockPlacement) with held sword.
     */
    public static void sendSingleBlockPacket() {
        ItemStack held = C.p().getHeldItem();
        if (held != null && held.getItem() instanceof ItemSword) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
            KeyBindingBridge.from(C.mc.gameSettings.keyBindUseItem).bridge$setDown(true);
            blocking = true;
            serverBlocking = true;
        }
    }

    public static void stopBlocking() {
        if (!blocking && !serverBlocking) return;

        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        KeyBindingBridge.from(C.mc.gameSettings.keyBindUseItem).bridge$setDown(false);
        if (C.mc.playerController != null && C.p() != null) {
            C.mc.playerController.onStoppedUsingItem(C.p());
        }

        serverBlocking = false;
        blocking = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (!C.isInGame() || C.p().isDead) {
            stopBlocking();
        }
    }

    @SubscribeEvent
    public static void onRespawn(RespawnEvent event) {
        stopBlocking();
    }

    private static boolean isHoldingSword() {
        ItemStack held = C.p().getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private static EntityLivingBase findTarget() {
        List<EntityLivingBase> targets = TargetUtil.getPossibleTargets(range, false, false);
        if (targets.isEmpty()) return null;

        targets.sort(Comparator.comparingDouble(TargetUtil::getDistanceToEntity));
        return targets.get(0);
    }

    @Override
    protected void onEnable() {
        blocking = false;
        serverBlocking = false;
        currentTarget = null;
        attackCounter = 0;
        nextAttackTime = 0;
        blockReleaseTime = 0;
    }

    @Override
    protected void onDisable() {
        stopBlocking();
    }
}
