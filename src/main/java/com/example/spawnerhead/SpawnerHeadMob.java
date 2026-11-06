package com.example.spawnerhead;

import com.example.advancement.ModAdvancements;
import com.example.threat.ThreatScale;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.SpawnerHeadCarrierEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rare zombie, skeleton, or wither skeleton with a {@link Items#SPAWNER} on the head.
 * Every {@link #HITS_TO_SUMMON} player hits summons more of the same mob nearby.
 * Zombies and skeletons summon {@link #SUMMON_COUNT_MIN}–{@link #SUMMON_COUNT_MAX}.
 * Wither skeleton carriers always summon {@link #WITHER_SUMMON_COUNT}.
 * Reinforcements never get a spawner on the head.
 */
public final class SpawnerHeadMob {

	// ===================== SETTINGS (change these numbers) =====================

	/** Enable mechanic. false — spawner head never appears. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) on day 100 that a zombie or skeleton spawn becomes a carrier (GROWING: 10× lower
	 * at start, see ThreatTable.DANGEROUS_START_RATIO). 0.005 = 0.5%, one in two hundred.
	 * Separate roll per zombie/skeleton. Wither skeletons use a different constant.
	 */
	public static final float CARRIER_SPAWN_CHANCE = 0.005f;

	/**
	 * Chance (0.0–1.0) on day 100 for wither skeleton spawner head (GROWING). 0.005 = 0.5%.
	 * Separate so you can tune Nether without changing Overworld. Command spawn skips the roll.
	 */
	public static final float WITHER_CARRIER_SPAWN_CHANCE = 0.005f;

	/** Player hits needed to trigger a summon (2 = two landed hits). Same for all carriers. */
	public static final int HITS_TO_SUMMON = 2;

	/** Min and max zombies or skeletons summoned per trigger (inclusive). */
	public static final int SUMMON_COUNT_MIN = 1;
	public static final int SUMMON_COUNT_MAX = 2;

	/**
	 * Wither skeletons summoned per trigger. 2 = always two, no randomness.
	 * Summoned mobs never get spawner heads themselves.
	 */
	public static final int WITHER_SUMMON_COUNT = 2;

	/** Radius (blocks) around the carrier where minions appear. */
	public static final double SUMMON_RADIUS = 3.0;

	/** Spawner head drop chance on death (like armor). */
	public static final float SPAWNER_HEAD_DROP_CHANCE = 0.085f;

	// ========================================================================

	private SpawnerHeadMob() {
	}

	public static boolean hasSpawnerOnHead(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.SPAWNER);
	}

	/** Call on zombie/skeleton finalizeSpawn (after armor). Returns true if spawner was equipped. */
	public static boolean tryEquipOnSpawn(Mob mob) {
		if (!ENABLED || !isEligibleType(mob) || !isEligibleSpecial(mob)) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), CARRIER_SPAWN_CHANCE)) {
			return false;
		}
		if (!(mob instanceof SpawnerHeadCarrierEntity carrier)) {
			return false;
		}
		// Summoned reinforcements get normal gear only — no spawner chain.
		if (carrier.evolvingthreats$isSpawnerReinforcement()) {
			return false;
		}
		equipSpawner(mob, carrier);
		return true;
	}

	/**
	 * Wither skeleton. Mod armor and weapons are skipped — spawner head only.
	 * Call from finalizeSpawn. Command sets the flag early and skips the roll.
	 */
	public static boolean tryEquipWitherSkeleton(Mob mob) {
		if (!ENABLED || !(mob instanceof WitherSkeleton)) {
			return false;
		}
		if (!(mob instanceof SpawnerHeadCarrierEntity carrier)) {
			return false;
		}
		if (carrier.evolvingthreats$isSpawnerHeadCarrier()) {
			return true;
		}
		if (carrier.evolvingthreats$isSpawnerReinforcement()) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), WITHER_CARRIER_SPAWN_CHANCE)) {
			return false;
		}
		equipSpawner(mob, carrier);
		return true;
	}

	/** Command: wither skeleton carrier in front of player. null — mechanic disabled. */
	public static @Nullable WitherSkeleton spawnWitherCarrier(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		WitherSkeleton skeleton = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			throw new IllegalStateException("Failed to create wither skeleton carrier");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (skeleton instanceof SpawnerHeadCarrierEntity carrier) {
			carrier.evolvingthreats$setSpawnerHeadCarrier(true);
			carrier.evolvingthreats$setSpawnerHeadHitCount(0);
		}
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		if (skeleton instanceof SpawnerHeadCarrierEntity carrier) {
			equipSpawner(skeleton, carrier);
		}
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return skeleton;
	}

	private static void equipSpawner(Mob mob, SpawnerHeadCarrierEntity carrier) {
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SPAWNER));
		mob.setDropChance(EquipmentSlot.HEAD, SPAWNER_HEAD_DROP_CHANCE);
		carrier.evolvingthreats$setSpawnerHeadCarrier(true);
		carrier.evolvingthreats$setSpawnerHeadHitCount(0);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}

	/** After successful damage to carrier (called from mixin). */
	public static void onPlayerHit(Mob mob, DamageSource source) {
		if (!ENABLED || !(mob instanceof SpawnerHeadCarrierEntity carrier)) {
			return;
		}
		if (!carrier.evolvingthreats$isSpawnerHeadCarrier() || !hasSpawnerOnHead(mob)) {
			return;
		}
		if (!(source.getEntity() instanceof Player)) {
			return;
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}

		int hits = carrier.evolvingthreats$getSpawnerHeadHitCount() + 1;
		carrier.evolvingthreats$setSpawnerHeadHitCount(hits);
		if (hits < HITS_TO_SUMMON) {
			return;
		}

		carrier.evolvingthreats$setSpawnerHeadHitCount(0);
		summonMinions(mob, level);
		if (source.getEntity() instanceof ServerPlayer player) {
			ModAdvancements.onReinforcements(player);
		}
	}

	private static void summonMinions(Mob carrier, ServerLevel level) {
		EntityType<? extends Mob> type = getSummonType(carrier);
		if (type == null) {
			return;
		}
		int count = carrier instanceof WitherSkeleton
				? Math.max(1, WITHER_SUMMON_COUNT)
				: carrier.getRandom().nextIntBetweenInclusive(SUMMON_COUNT_MIN, SUMMON_COUNT_MAX);
		for (int i = 0; i < count; i++) {
			spawnOneMinion(level, carrier, type);
		}
	}

	private static void spawnOneMinion(ServerLevel level, Mob carrier, EntityType<? extends Mob> type) {
		Mob minion = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (minion == null) {
			return;
		}
		if (minion instanceof SpawnerHeadCarrierEntity reinforcement) {
			reinforcement.evolvingthreats$setSpawnerReinforcement(true);
		}
		Vec3 pos = findSpawnPos(level, carrier);
		minion.snapTo(pos.x, pos.y, pos.z, carrier.getRandom().nextFloat() * 360.0f, 0.0f);
		minion.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(minion.blockPosition()),
				EntitySpawnReason.MOB_SUMMONED,
				(SpawnGroupData) null);
		level.addFreshEntity(minion);
	}

	private static Vec3 findSpawnPos(ServerLevel level, Mob carrier) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double dx = (carrier.getRandom().nextDouble() * 2.0 - 1.0) * SUMMON_RADIUS;
			double dz = (carrier.getRandom().nextDouble() * 2.0 - 1.0) * SUMMON_RADIUS;
			double x = carrier.getX() + dx;
			double z = carrier.getZ() + dz;
			double y = carrier.getY();
			if (level.noCollision(minionBox(typeForCarrier(carrier), x, y, z))) {
				return new Vec3(x, y, z);
			}
		}
		return carrier.position();
	}

	private static EntityType<? extends Mob> typeForCarrier(Mob carrier) {
		return getSummonType(carrier);
	}

	private static AABB minionBox(EntityType<? extends Mob> type, double x, double y, double z) {
		var dims = type.getDimensions();
		float w = dims.width() / 2.0f;
		float h = dims.height();
		return new AABB(x - w, y, z - w, x + w, y + h, z + w);
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends Mob> getSummonType(Mob mob) {
		return (EntityType<? extends Mob>) mob.getType();
	}

	private static boolean isEligibleType(Mob mob) {
		if (mob instanceof Drowned || mob instanceof ZombifiedPiglin) {
			return false;
		}
		if (mob instanceof Zombie) {
			return true;
		}
		return mob instanceof Skeleton || mob instanceof Stray;
	}

	private static boolean isEligibleSpecial(Mob mob) {
		if (mob instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		return !(mob instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
	}
}
