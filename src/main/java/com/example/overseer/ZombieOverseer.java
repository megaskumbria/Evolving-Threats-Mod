package com.example.overseer;

import com.example.MobNames;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 Rare mini-boss: "Rotten overseer."
 * Settings and equipment are here; AI is at {@code Overseer*Goal}.
 */
public final class ZombieOverseer {

	// ===================== SETTINGS (change these numbers) =====================

	/*A chance at the spawn of an ordinary zombie to become a guard. 0.002 = 0.2% (was 0.6%, too often). */
	public static final float SPAWN_CHANCE = 0.002f;

	/*Health. 100 = 50 hearts. The random 20-40 zombies do not remain. */
	public static final float MAX_HEALTH = 100.0f;

	/*The radius in which it holds/seeks the target(s). */
	public static final double FOLLOW_RANGE = 112.0;

	/*Next, the hunt is dropped (blocks). */
	public static final double HUNT_ABANDON_DISTANCE = 128.0;

	/*How many tics (20/s) to hunt if the player is too far away all the time – 2 minutes */
	public static final int HUNT_TIMEOUT_TICKS = 20 * 60 * 2;

	public static final double SUMMON_PLAYER_MAX_DISTANCE = 96.0;

	public static final int SUMMON_COOLDOWN_TICKS = 25 * 20;

	/**
	 If there is no free space and zombies did not appear, the pause is shorter.
	 * 100 tics = 5 seconds. Otherwise, he would have waited a full 25 seconds against the wall for nothing.
	 * 20 ticks = 1 second.
	 */
	public static final int SUMMON_FAIL_COOLDOWN_TICKS = 100;

	public static final int SUMMON_ZOMBIES_PER_CAST = 2;

	/**
	 How far from the overseer to look for a place for an assistant, in blocks.
	 1.5 and 4.0 = one and a half to four blocks to the side.
	 */
	public static final double SUMMON_RADIUS_MIN = 1.5;
	public static final double SUMMON_RADIUS_MAX = 4.0;

	/**
	 How many random spots to go around before refusing.
	 12 in the hallway. It takes longer to find out if it’s too crowded.
	 */
	public static final int SUMMON_SPOT_TRIES = 12;

	/**
	 How many blocks up and down from the overseer's feet still look at the floor?
	 4 = does not put zombies in the ceiling and does not drop into a hole deeper than four blocks.
	 */
	public static final int SUMMON_VERTICAL_STEPS = 4;

	public static final int MAX_SERVANTS_ALIVE = 5;

	public static final int ARMOR_PROTECTION_LEVEL = 2;

	public static final int MAIN_AXE_SHARPNESS = 3;

	/*Axe sharpness in the second hand. It adds to the impact along with the damage to the axe itself. */
	public static final int OFFHAND_AXE_SHARPNESS = 2;

	// ========================================================================

	public static final String DISPLAY_NAME = "Rotten Overseer";

	private ZombieOverseer() {
	}

	public static boolean tryCreateOnSpawn(Zombie zombie, ServerLevel level) {
		if (!(zombie instanceof ZombieOverseerEntity overseer)) {
			return false;
		}
		if (overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		// From what day do overseers even appear (ThreatTable.OVERSEER DAY)? The chance lower from the day does not change.
		if (!ThreatScale.reached(level, ThreatTable.OVERSEER_DAY)) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}

		applyOverseer(zombie, level);
		Player hunt = findNearestPlayer(level, zombie, HUNT_ABANDON_DISTANCE);
		if (hunt != null) {
			overseer.evolvingthreats$setHuntTargetId(hunt.getUUID());
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			zombie.setTarget(hunt);
		}
		return true;
	}

	/**
	 Spawn overseer command/egg (100% mini-boss, no random loot).
	 *
	 * @param hunter player-target; if null is the closest in hunting radius
	 */
	public static Zombie spawnOverseer(ServerLevel level, Vec3 pos, @Nullable ServerPlayer hunter) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create zombie for overseer spawn");
		}

		zombie.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		applyOverseer(zombie, level);

		ServerPlayer huntTarget = hunter;
		if (huntTarget == null) {
			huntTarget = (ServerPlayer) findNearestPlayer(level, zombie, HUNT_ABANDON_DISTANCE);
		}
		if (huntTarget != null && zombie instanceof ZombieOverseerEntity overseer) {
			overseer.evolvingthreats$setHuntTargetId(huntTarget.getUUID());
			overseer.evolvingthreats$setHuntStartedGameTime(level.getGameTime());
			zombie.setTarget(huntTarget);
		}

		level.addFreshEntity(zombie);
		return zombie;
	}

	public static void applyOverseer(Zombie zombie, ServerLevel level) {
		if (!(zombie instanceof ZombieOverseerEntity overseer)) {
			return;
		}
		overseer.evolvingthreats$setOverseer(true);
		overseer.evolvingthreats$setActiveServantCount(0);

		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MAX_HEALTH);
		zombie.setHealth(MAX_HEALTH);
		zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE);

		zombie.setCustomName(Component.literal(DISPLAY_NAME));
		MobNames.showOnlyWhenLookedAt(zombie);

		equipArmor(zombie, level);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, enchantAxe(level, MAIN_AXE_SHARPNESS));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, enchantAxe(level, OFFHAND_AXE_SHARPNESS));

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor()) {
				zombie.setDropChance(slot, 0.12f);
			}
		}
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.12f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
	}

	/**
	 An already living overseer at loading the world.
	 * If the stock is less {@link #MAX HEALTH}, picks it up.
	 How many hearts have already been removed – so much remains removed.
	 */
	public static void raiseStoredHealth(Zombie zombie) {
		var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getBaseValue() >= MAX_HEALTH) {
			return;
		}
		float missing = (float) (maxHealth.getBaseValue() - zombie.getHealth());
		if (missing < 0.0f) {
			missing = 0.0f;
		}
		maxHealth.setBaseValue(MAX_HEALTH);
		zombie.setHealth(Math.max(1.0f, MAX_HEALTH - missing));
	}

	private static void equipArmor(Zombie zombie, ServerLevel level) {
		zombie.setItemSlot(EquipmentSlot.HEAD, enchantArmor(level, Items.DIAMOND_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, enchantArmor(level, Items.DIAMOND_CHESTPLATE));
		zombie.setItemSlot(EquipmentSlot.LEGS, enchantArmor(level, Items.DIAMOND_LEGGINGS));
		zombie.setItemSlot(EquipmentSlot.FEET, enchantArmor(level, Items.DIAMOND_BOOTS));
	}

	private static ItemStack enchantArmor(ServerLevel level, net.minecraft.world.level.ItemLike item) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> protection = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.PROTECTION);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(protection, ARMOR_PROTECTION_LEVEL));
		return stack;
	}

	private static ItemStack enchantAxe(ServerLevel level, int sharpness) {
		ItemStack stack = new ItemStack(Items.DIAMOND_AXE);
		Holder<Enchantment> sharp = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(sharp, sharpness));
		return stack;
	}

	public static Player findNearestPlayer(Level level, Zombie zombie, double maxDistance) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		double maxDistSqr = maxDistance * maxDistance;
		AABB box = zombie.getBoundingBox().inflate(maxDistance);
		List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class, box,
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

		return players.stream()
				.filter(p -> zombie.distanceToSqr(p) <= maxDistSqr)
				.min(Comparator.comparingDouble(zombie::distanceToSqr))
				.orElse(null);
	}

	/*Counts conscripted zombies next to the same target (for servant limit). */
	public static void refreshServantCount(Zombie overseer, ServerLevel level) {
		if (!(overseer instanceof ZombieOverseerEntity data) || !data.evolvingthreats$isOverseer()) {
			return;
		}
		var huntId = data.evolvingthreats$getHuntTargetId();
		int count = (int) level.getEntitiesOfClass(Zombie.class, overseer.getBoundingBox().inflate(48), z -> {
			if (z == overseer) {
				return false;
			}
			if (z instanceof ZombieOverseerEntity other && other.evolvingthreats$isOverseer()) {
				return false;
			}
			if (huntId == null) {
				return z.getTarget() != null;
			}
			return z.getTarget() != null && huntId.equals(z.getTarget().getUUID());
		}).size();
		data.evolvingthreats$setActiveServantCount(count);
	}
}
