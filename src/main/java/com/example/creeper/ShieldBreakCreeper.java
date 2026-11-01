package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.entity.ShieldBreakCreeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 A rare cryper who behaves like a regular: runs up and explodes with the usual fuse and force.
 If the player closed with a shield and the explosion hit the shield - the shield is destroyed completely, the damage to the player does not pass.
 The strength and enchantment of the “Durability” shield does not spawn: it always breaks with one such explosion.
 Looks like a vanilla cryper.
 */
public final class ShieldBreakCreeper {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - such crypers do not appear, the team does not make a scraper either. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) when spawning an ordinary cryper to become a shield breaker. 0.05 = 5%.
	 Like a bomber and a skeleton with a beacon pumpkin. If a bomber or a thrower has already fallen, this chance is not thrown.
	 A loaded cryper with a shield scrapper could be.
	 * The Spawn team does not take the chance.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	// ========================================================================

	private ShieldBreakCreeper() {
	}

	public static boolean isShieldBreak(Creeper creeper) {
		return ENABLED
				&& creeper instanceof ShieldBreakCreeperEntity data
				&& data.evolvingthreats$isShieldBreakCreeper();
	}

	/** Call from finalizeSpawn. The team spawn puts the flag in advance. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof ShieldBreakCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isShieldBreakCreeper()) {
			return;
		}
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND) {
			return;
		}
		if (creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/** Team: Breaker of the shield in front of the player. */
	public static Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create shield-break creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof ShieldBreakCreeperEntity data) {
			data.evolvingthreats$setShieldBreakCreeper(true);
		}
		creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(creeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static void apply(Creeper creeper) {
		if (creeper instanceof ShieldBreakCreeperEntity data) {
			data.evolvingthreats$setShieldBreakCreeper(true);
		}
	}

	/**
	 Destroy the shield in the hand with which the player closed.
	 Explosion damage is not added here: the game has already considered it blocked.
	 If the shield is not closed or the explosion in the shield is not hit - this method is not called.
	 */
	public static void breakBlockingShield(Player player) {
		if (!(player.level() instanceof ServerLevel)) {
			return;
		}
		InteractionHand hand = player.getUsedItemHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.SHIELD)) {
			return;
		}
		ItemStack broken = stack.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		player.onEquippedItemBroken(broken, hand.asEquipmentSlot());
		player.stopUsingItem();
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
