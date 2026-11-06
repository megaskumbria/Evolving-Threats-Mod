package com.example.zombie;

import com.example.advancement.ModAdvancements;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.entity.ZombieSorcererEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rare zombie in black leather with a netherite hoe.
 * Gets no mod enchants. Hoe never drops on death.
 * Every few seconds, while it sees the player, casts one spell.
 * Most often launch upward; less often ignite or pull toward itself.
 */
public final class ZombieSorcerer {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable mechanic. false — no sorcerers, command spawn disabled. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) that a normal zombie becomes a sorcerer. 0.001 = 0.1%, one in a thousand.
	 * FIXED from day 40 (ThreatTable.SORCERER_DAY); does not grow with threat scale.
	 * Drowned, husk, zombie villager, baby, overseer, and raid skip this roll.
	 * Command spawn skips the roll. 0 = command only.
	 */
	public static final float SPAWN_CHANCE = 0.001f;

	/**
	 * Health. 80 = 40 hearts. Normal mod zombies roll 20–40;
	 * sorcerers always use this value instead.
	 * Below 1 the game kills them almost instantly.
	 */
	public static final float HEALTH = 80.0f;

	/**
	 * XP on kill. 15 — three times normal zombie (5).
	 * Drops only if a player killed it (vanilla rule).
	 */
	public static final int XP_REWARD = 15;

	/**
	 * Spell range in blocks. 12 = player not melee range but close.
	 * Beyond this the sorcerer walks like a normal zombie. 0 = no spells.
	 */
	public static final double SPELL_RANGE_BLOCKS = 12.0;

	/**
	 * Cooldown between spells, seconds. 8 = next ignite, launch, or pull not before 8 seconds.
	 * 0 = every tick while player in range — endless launching.
	 * Cooldown does not tick while target is not visible; first spell when line of sight returns.
	 */
	public static final int SPELL_COOLDOWN_SECONDS = 8;

	/**
	 * Fire duration after ignite spell, seconds. 6 = six seconds.
	 * Fire Resistance works as usual. Shield does not block this spell.
	 */
	public static final int FIRE_SECONDS = 6;

	/**
	 * Spell weights (sum 100 → launch 70%, fire 15%, pull 15%).
	 * Launch is primary. Weight 0 = spell disabled.
	 * If player is closer than {@link #PULL_MIN_BLOCKS}, pull is excluded
	 * and launch/fire share the remaining weight.
	 */
	public static final int LAUNCH_WEIGHT = 70;
	public static final int FIRE_WEIGHT = 15;
	public static final int PULL_WEIGHT = 15;

	/**
	 * Normal launch height in blocks, inclusive. 25–30 equally likely.
	 * No potions assumed. Jump Boost raises higher, Slow Falling too.
	 * Falling from 25 blocks is near-lethal (fall damage = height − 3).
	 */
	public static final int LAUNCH_MIN_BLOCKS = 25;
	public static final int LAUNCH_MAX_BLOCKS = 30;

	/**
	 * Rare launch height in blocks. 50 = well above normal 25–30.
	 * Rolled with {@link #LAUNCH_RARE_CHANCE}; otherwise normal range.
	 */
	public static final int LAUNCH_RARE_BLOCKS = 50;

	/**
	 * Chance (0.0–1.0) to launch to {@link #LAUNCH_RARE_BLOCKS} instead of 25–30.
	 * 0.10 = 10%, roughly every tenth launch. 0 = always 25–30. 1 = always 50.
	 */
	public static final float LAUNCH_RARE_CHANCE = 0.10f;

	/**
	 * Pull strength toward sorcerer. 1.6 = strong tug, not a teleport.
	 * Higher — player slides almost to the zombie. 0 = pull does nothing.
	 * Knockback resistance does not weaken this — it is not a melee hit.
	 */
	public static final double PULL_SPEED = 1.6;

	/**
	 * Pull is not chosen closer than this (blocks) — player already in melee range.
	 * Then ignite or launch is used. 2.5 ≈ strike distance.
	 */
	public static final double PULL_MIN_BLOCKS = 2.5;

	/**
	 * Chance (0.0–1.0) for one extra loot item when killed by a player. 1.0 = always.
	 * 0 = XP only. Burned or exploded — no bonus drop.
	 * One random item, equal weights: sugar, golden carrot, firework star,
	 * redstone torch, or one black leather piece.
	 * Worn armor and hoe never drop from this roll.
	 */
	public static final float BONUS_DROP_CHANCE = 1.0f;

	// ========================================================================

	/** Gravity per tick, like airborne player. Used to compute launch velocity. */
	private static final double GRAVITY_PER_TICK = 0.08;
	private static final double VERTICAL_DRAG = 0.98;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	private static final Item[] LEATHER = {
			Items.LEATHER_HELMET,
			Items.LEATHER_CHESTPLATE,
			Items.LEATHER_LEGGINGS,
			Items.LEATHER_BOOTS
	};

	/** Sugar, golden carrot, firework star, redstone torch. Leather rolled separately as one piece. */
	private static final Item[] BONUS_ITEMS = {
			Items.SUGAR,
			Items.GOLDEN_CARROT,
			Items.FIREWORK_STAR,
			Items.REDSTONE_TORCH
	};

	private ZombieSorcerer() {
	}

	public static boolean isSorcerer(Zombie zombie) {
		return ENABLED
				&& zombie instanceof ZombieSorcererEntity data
				&& data.evolvingthreats$isSorcerer();
	}

	/**
	 * End of zombie spawn. true = sorcerer: skip random weapon, armor, enchants,
	 * shield, totem, pumpkin, and spawner head.
	 * Potion buffs may already be applied like a normal zombie.
	 */
	public static boolean tryClaim(Zombie zombie, EntitySpawnReason reason) {
		if (!canBeSorcerer(zombie)) {
			return false;
		}
		ZombieSorcererEntity data = (ZombieSorcererEntity) zombie;
		if (data.evolvingthreats$isSorcerer()) {
			equip(zombie);
			return true;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return false;
		}
		// Sorcerers only from SORCERER_DAY; chance does not scale by day.
		if (!ThreatScale.reached(zombie.level(), ThreatTable.SORCERER_DAY)) {
			return false;
		}
		if (zombie.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		data.evolvingthreats$setSorcerer(true);
		equip(zombie);
		return true;
	}

	/** XP to award. -1 = not a sorcerer, let vanilla decide. */
	public static int experienceReward(Zombie zombie) {
		if (!isSorcerer(zombie)) {
			return -1;
		}
		return Math.max(0, XP_REWARD);
	}

	/** Bonus loot on death. Hoe is excluded. */
	public static void dropBonus(Zombie zombie, ServerLevel level, boolean killedByPlayer) {
		if (!isSorcerer(zombie) || !killedByPlayer || BONUS_DROP_CHANCE <= 0.0f) {
			return;
		}
		if (zombie.getRandom().nextFloat() >= BONUS_DROP_CHANCE) {
			return;
		}
		zombie.spawnAtLocation(level, rollBonus(zombie));
	}

	/** End of target tick. Tracks cooldown and casts when player is in range with line of sight. */
	public static void tick(Zombie zombie) {
		if (!(zombie instanceof ZombieSorcererEntity data) || !data.evolvingthreats$isSorcerer()) {
			return;
		}
		int cooldown = data.evolvingthreats$getSorcererCooldown();
		if (cooldown > 0) {
			data.evolvingthreats$setSorcererCooldown(cooldown - 1);
			return;
		}
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(zombie.getTarget() instanceof Player player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (SPELL_RANGE_BLOCKS <= 0.0 || zombie.distanceTo(player) > SPELL_RANGE_BLOCKS) {
			return;
		}
		if (!zombie.hasLineOfSight(player)) {
			return;
		}
		cast(level, zombie, player);
		data.evolvingthreats$setSorcererCooldown(Math.max(0, SPELL_COOLDOWN_SECONDS) * 20);
	}

	/** Command: sorcerer in front of player. null — mechanic disabled. */
	public static @Nullable Zombie spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			throw new IllegalStateException("Failed to create zombie sorcerer");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		zombie.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (zombie instanceof ZombieSorcererEntity data) {
			data.evolvingthreats$setSorcerer(true);
		}
		zombie.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(zombie.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (lookAt != null) {
			zombie.setTarget(lookAt);
		}
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static boolean canBeSorcerer(Zombie zombie) {
		if (!ENABLED || !(zombie instanceof ZombieSorcererEntity)) {
			return false;
		}
		if (zombie.getType() != EntityTypes.ZOMBIE || zombie.isBaby()) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}

	private static void equip(Zombie zombie) {
		zombie.setCanPickUpLoot(false);
		ItemStack hoe = new ItemStack(Items.NETHERITE_HOE);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, hoe);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			zombie.setItemSlot(ARMOR_SLOTS[i], blackLeather(LEATHER[i]));
			zombie.setDropChance(ARMOR_SLOTS[i], 0.0f);
		}
		var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			float health = Math.max(1.0f, HEALTH);
			maxHealth.setBaseValue(health);
			zombie.setHealth(health);
		}
	}

	private static void cast(ServerLevel level, Zombie zombie, Player player) {
		switch (pickSpell(zombie, player)) {
			case 0 -> launch(level, player, zombie);
			case 1 -> ignite(level, player);
			default -> pull(level, zombie, player);
		}
		level.playSound(
				null,
				zombie.getX(),
				zombie.getY(),
				zombie.getZ(),
				SoundEvents.EVOKER_CAST_SPELL,
				SoundSource.HOSTILE,
				1.0f,
				0.8f + zombie.getRandom().nextFloat() * 0.4f);
	}

	private static void ignite(ServerLevel level, Player player) {
		player.igniteForSeconds(Math.max(1, FIRE_SECONDS));
		level.sendParticles(
				ParticleTypes.FLAME,
				player.getX(),
				player.getY() + 1.0,
				player.getZ(),
				16,
				0.35,
				0.5,
				0.35,
				0.02);
	}

	/** 0 = launch, 1 = fire, 2 = pull. Launch is most common. */
	private static int pickSpell(Zombie zombie, Player player) {
		boolean allowPull = PULL_WEIGHT > 0 && zombie.distanceTo(player) >= PULL_MIN_BLOCKS;
		int launch = Math.max(0, LAUNCH_WEIGHT);
		int fire = Math.max(0, FIRE_WEIGHT);
		int pull = allowPull ? Math.max(0, PULL_WEIGHT) : 0;
		int total = launch + fire + pull;
		if (total <= 0) {
			return 0;
		}
		int roll = zombie.getRandom().nextInt(total);
		if (roll < launch) {
			return 0;
		}
		roll -= launch;
		if (roll < fire) {
			return 1;
		}
		return 2;
	}

	private static void launch(ServerLevel level, Player player, Zombie zombie) {
		int blocks = launchBlocks(zombie);
		double vy = velocityForHeight(blocks);
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, vy, motion.z);
		player.resetFallDistance();
		player.syncVelocity = true;
		if (player instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onLifted(serverPlayer);
		}
		level.sendParticles(
				ParticleTypes.WITCH,
				player.getX(),
				player.getY(),
				player.getZ(),
				20,
				0.3,
				0.2,
				0.3,
				0.05);
	}

	private static void pull(ServerLevel level, Zombie zombie, Player player) {
		Vec3 toward = new Vec3(zombie.getX() - player.getX(), 0.0, zombie.getZ() - player.getZ());
		double len = toward.length();
		if (len < 1.0E-4 || PULL_SPEED <= 0.0) {
			return;
		}
		Vec3 vel = toward.scale(PULL_SPEED / len);
		player.setDeltaMovement(vel.x, Math.max(0.2, player.getDeltaMovement().y), vel.z);
		player.syncVelocity = true;
		if (player instanceof ServerPlayer serverPlayer) {
			ModAdvancements.onLifted(serverPlayer);
		}
		level.sendParticles(
				ParticleTypes.WITCH,
				player.getX(),
				player.getY() + 1.0,
				player.getZ(),
				14,
				0.4,
				0.4,
				0.4,
				0.0);
	}

	/** Usually 25–30 equally. Rarely exactly {@link #LAUNCH_RARE_BLOCKS}. */
	private static int launchBlocks(Zombie zombie) {
		if (LAUNCH_RARE_CHANCE > 0.0f && zombie.getRandom().nextFloat() < LAUNCH_RARE_CHANCE) {
			return Math.max(0, LAUNCH_RARE_BLOCKS);
		}
		int min = Math.min(LAUNCH_MIN_BLOCKS, LAUNCH_MAX_BLOCKS);
		int max = Math.max(LAUNCH_MIN_BLOCKS, LAUNCH_MAX_BLOCKS);
		return min + zombie.getRandom().nextInt(max - min + 1);
	}

	/**
	 * Upward velocity to reach roughly the target height without potions.
	 * Game moves the player, then applies −0.08 gravity and ×0.98 drag each tick.
	 */
	private static double velocityForHeight(double blocks) {
		if (blocks <= 0.0) {
			return 0.0;
		}
		double low = 0.0;
		double high = 4.0;
		for (int i = 0; i < 24; i++) {
			double mid = (low + high) * 0.5;
			if (peakHeight(mid) < blocks) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) * 0.5;
	}

	private static double peakHeight(double vy) {
		double y = 0.0;
		for (int i = 0; i < 200 && vy > 0.0; i++) {
			y += vy;
			vy = (vy - GRAVITY_PER_TICK) * VERTICAL_DRAG;
		}
		return y;
	}

	private static ItemStack rollBonus(Zombie zombie) {
		int roll = zombie.getRandom().nextInt(BONUS_ITEMS.length + 1);
		if (roll < BONUS_ITEMS.length) {
			return new ItemStack(BONUS_ITEMS[roll]);
		}
		return blackLeather(LEATHER[zombie.getRandom().nextInt(LEATHER.length)]);
	}

	private static ItemStack blackLeather(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(DyeColor.BLACK.getFireworkColor()));
		return stack;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
