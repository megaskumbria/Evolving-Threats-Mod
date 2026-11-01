package com.example.raid;

import com.example.MobNames;
import com.example.entity.RaidParticipantEntity;
import com.example.overseer.ZombieOverseer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Raid anchor event: Golden hoe anchor + 12-15 servants with {@link RaidEliteLoadout}
 */
public final class RaidAnchor {

	// ===================== SETTINGS (change these numbers) =====================

	/** Anchor max health. 100 = 50 hearts. Weapon is always a golden hoe. */
	public static final float ANCHOR_MAX_HEALTH = 100.0f;

	/** Minimum and maximum minion count (inclusive). */
	public static final int MINION_COUNT_MIN = 12;
	public static final int MINION_COUNT_MAX = 15;

	/** Fraction of minions that are zombies; the rest are skeletons. 0.60 = 60% zombies. */
	public static final float MINION_ZOMBIE_FRACTION = 0.60f;

	/** Minion spawn radius around the anchor (blocks). */
	public static final double MINION_SPAWN_RADIUS_MIN = 6.0;
	public static final double MINION_SPAWN_RADIUS_MAX = 14.0;

	/** How far from anchor/minions hunt GPS still tracks a player (blocks). */
	public static final double HUNT_MAX_DISTANCE = 128.0;

	/** After anchor death: Slowness II on minions. Seconds × 20 = ticks. */
	public static final int MINION_SLOWNESS_TICKS = 30 * 20;

	/** Slowness amplifier: 0 = I, 1 = II. */
	public static final int MINION_SLOWNESS_AMPLIFIER = 1;

	// ========================================================================

	public static final String DISPLAY_NAME = "Raid Anchor";

	private RaidAnchor() {
	}

	public static void spawnRaid(ServerLevel level, Vec3 anchorPos, @Nullable ServerPlayer hunter) {
		UUID raidId = UUID.randomUUID();
		ServerPlayer target = hunter;
		if (target == null) {
			Zombie probe = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			if (probe != null) {
				probe.snapTo(anchorPos.x, anchorPos.y, anchorPos.z, 0, 0);
				target = (ServerPlayer) ZombieOverseer.findNearestPlayer(level, probe, HUNT_MAX_DISTANCE);
			}
		}

		UUID huntId = target != null ? target.getUUID() : null;

		Zombie anchor = spawnAnchor(level, anchorPos, raidId, huntId);
		if (anchor == null) {
			return;
		}
		RaidRegistry.registerAnchor(raidId, anchor.getId());

		spawnMinions(level, anchorPos, raidId, huntId, false);

		if (huntId != null && target != null) {
			anchor.setTarget(target);
		}
	}

	/**
	 * Natural spawn: a plain zombie that just spawned becomes an anchor
	 * (like a zombie becoming an overseer) and minions appear around it.
	 * Called from {@link RaidAnchorNatural}. Minions are placed on the surface, not at anchor height.
	 *
	 * @param zombie fresh zombie not yet added to the world (the game adds it)
	 * @param hunter nearest player — raid target; may be null
	 */
	public static void convertToAnchor(ServerLevel level, Zombie zombie, @Nullable ServerPlayer hunter) {
		UUID raidId = UUID.randomUUID();
		UUID huntId = hunter != null ? hunter.getUUID() : null;

		setupAnchor(zombie, raidId, huntId);
		RaidRegistry.registerAnchor(raidId, zombie.getId());

		spawnMinions(level, zombie.position(), raidId, huntId, true);

		if (hunter != null) {
			zombie.setTarget(hunter);
		}
	}

	/** Spawns minions in a ring around the anchor. surface=true — ground height (natural raid). */
	private static void spawnMinions(ServerLevel level, Vec3 anchorPos, UUID raidId, @Nullable UUID huntId, boolean surface) {
		var random = level.getRandom();
		int count = MINION_COUNT_MIN + random.nextInt(MINION_COUNT_MAX - MINION_COUNT_MIN + 1);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = MINION_SPAWN_RADIUS_MIN + random.nextDouble()
					* (MINION_SPAWN_RADIUS_MAX - MINION_SPAWN_RADIUS_MIN);
			double x = anchorPos.x + Math.cos(angle) * radius;
			double z = anchorPos.z + Math.sin(angle) * radius;
			double y = anchorPos.y;
			if (surface) {
				// Without this, on hilly terrain, the servants would be inside the slope or in the air.
				y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, y, z)).getY();
			}
			Vec3 minionPos = new Vec3(x, y, z);

			if (random.nextFloat() < MINION_ZOMBIE_FRACTION) {
				spawnZombieMinion(level, minionPos, raidId, huntId);
			} else {
				spawnSkeletonMinion(level, minionPos, raidId, huntId);
			}
		}
	}

	@Nullable
	private static Zombie spawnAnchor(ServerLevel level, Vec3 pos, UUID raidId, @Nullable UUID huntId) {
		Zombie anchor = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (anchor == null) {
			return null;
		}
		anchor.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		setupAnchor(anchor, raidId, huntId);

		level.addFreshEntity(anchor);
		return anchor;
	}

	/** Configures a zombie as anchor: raid flags, health, name, golden hoe. Does not add to the world. */
	private static void setupAnchor(Zombie anchor, UUID raidId, @Nullable UUID huntId) {
		if (anchor instanceof RaidParticipantEntity raid) {
			raid.evolvingthreats$setRaidAnchor(true);
			raid.evolvingthreats$setRaidMinion(false);
			raid.evolvingthreats$setRaidId(raidId);
			raid.evolvingthreats$setRaidHuntTargetId(huntId);
			raid.evolvingthreats$setRaidGpsActive(true);
		}

		anchor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ANCHOR_MAX_HEALTH);
		anchor.setHealth(ANCHOR_MAX_HEALTH);
		anchor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(HUNT_MAX_DISTANCE);
		anchor.setCustomName(Component.literal(DISPLAY_NAME));
		MobNames.showOnlyWhenLookedAt(anchor);
		anchor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_HOE));
		anchor.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	/**
	 * Existing anchor when the world loads.
	 * If max health is below {@link #ANCHOR_MAX_HEALTH}, raises it.
	 * Missing health stays missing (hearts already lost remain lost).
	 */
	public static void raiseStoredHealth(Zombie anchor) {
		var maxHealth = anchor.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getBaseValue() >= ANCHOR_MAX_HEALTH) {
			return;
		}
		float missing = (float) (maxHealth.getBaseValue() - anchor.getHealth());
		if (missing < 0.0f) {
			missing = 0.0f;
		}
		maxHealth.setBaseValue(ANCHOR_MAX_HEALTH);
		anchor.setHealth(Math.max(1.0f, ANCHOR_MAX_HEALTH - missing));
	}

	private static void spawnZombieMinion(ServerLevel level, Vec3 pos, UUID raidId, @Nullable UUID huntId) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			return;
		}
		applyMinion(zombie, level, pos, raidId, huntId);
		RaidEliteLoadout.equipZombie(zombie, level);
		level.addFreshEntity(zombie);
	}

	private static void spawnSkeletonMinion(ServerLevel level, Vec3 pos, UUID raidId, @Nullable UUID huntId) {
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return;
		}
		applyMinion(skeleton, level, pos, raidId, huntId);
		RaidEliteLoadout.equipSkeleton(skeleton, level);
		skeleton.reassessWeaponGoal();
		level.addFreshEntity(skeleton);
	}

	private static void applyMinion(LivingEntity mob, ServerLevel level, Vec3 pos, UUID raidId, @Nullable UUID huntId) {
		mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
		if (mob instanceof RaidParticipantEntity raid) {
			raid.evolvingthreats$setRaidAnchor(false);
			raid.evolvingthreats$setRaidMinion(true);
			raid.evolvingthreats$setRaidId(raidId);
			raid.evolvingthreats$setRaidHuntTargetId(huntId);
			raid.evolvingthreats$setRaidGpsActive(true);
		}
		mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(HUNT_MAX_DISTANCE);
	}

	/**
	 * True when at least one servant of this raid is still alive near the anchor.
	 * The advancement requires this: killing the anchor after the servants are already
	 * dead does not count. Only loaded mobs are found (same box as {@link #disbandRaidNear}).
	 */
	public static boolean hasLivingMinion(ServerLevel level, UUID raidId, Vec3 center) {
		if (raidId == null) {
			return false;
		}
		double boxSize = HUNT_MAX_DISTANCE + 48;
		var box = new net.minecraft.world.phys.AABB(center, center).inflate(boxSize);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (!entity.isAlive() || !(entity instanceof RaidParticipantEntity raid)) {
				continue;
			}
			if (raid.evolvingthreats$isRaidMinion() && raidId.equals(raid.evolvingthreats$getRaidId())) {
				return true;
			}
		}
		return false;
	}

	/** Called when an anchor dies: slowness on minions, GPS off, targets cleared. */
	public static void disbandRaidNear(ServerLevel level, UUID raidId, Vec3 center) {
		RaidRegistry.unregister(raidId);

		double boxSize = HUNT_MAX_DISTANCE + 48;
		var box = new net.minecraft.world.phys.AABB(center, center).inflate(boxSize);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (!(entity instanceof RaidParticipantEntity raid)) {
				continue;
			}
			if (!raid.evolvingthreats$isRaidMinion()) {
				continue;
			}
			if (raidId == null || !raidId.equals(raid.evolvingthreats$getRaidId())) {
				continue;
			}
			raid.evolvingthreats$setRaidGpsActive(false);
			raid.evolvingthreats$setRaidHuntTargetId(null);
			if (entity instanceof Mob mob) {
				mob.setTarget(null);
			}
			entity.addEffect(new MobEffectInstance(
					MobEffects.SLOWNESS,
					MINION_SLOWNESS_TICKS,
					MINION_SLOWNESS_AMPLIFIER
			));
		}
	}
}
