package com.example.raid;

import com.example.entity.RaidParticipantEntity;
import com.example.overseer.ZombieOverseer;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Событие «Рейдовый якорь»: слабый якорь + 12–15 прислуг с {@link RaidEliteLoadout}.
 */
public final class RaidAnchor {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	public static final float ANCHOR_MAX_HEALTH = 35.0f;

	/** Минимум и максимум прислуг (включительно). */
	public static final int MINION_COUNT_MIN = 12;
	public static final int MINION_COUNT_MAX = 15;

	/** Доля зомби среди прислуг; остальное — скелеты. 0.60 = 60% зомби. */
	public static final float MINION_ZOMBIE_FRACTION = 0.60f;

	/** Радиус разброса прислуг вокруг якоря (блоки). */
	public static final double MINION_SPAWN_RADIUS_MIN = 6.0;
	public static final double MINION_SPAWN_RADIUS_MAX = 14.0;

	/** Как далеко от якоря/прислуг ещё работает «GPS» на игрока (блоки). */
	public static final double HUNT_MAX_DISTANCE = 128.0;

	public static final double BOSS_BAR_SHOW_DISTANCE = 64.0;

	/** После смерти якоря: Slowness II на прислуг, секунды × 20 = тики. */
	public static final int MINION_SLOWNESS_TICKS = 30 * 20;

	/** Уровень Slowness: 0 = I, 1 = II. */
	public static final int MINION_SLOWNESS_AMPLIFIER = 1;

	// ========================================================================

	public static final String DISPLAY_NAME = "Raid Anchor";
	public static final String BOSS_BAR_SUBTITLE = "The horde hears you";

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

		var random = level.getRandom();
		int count = MINION_COUNT_MIN + random.nextInt(MINION_COUNT_MAX - MINION_COUNT_MIN + 1);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = MINION_SPAWN_RADIUS_MIN + random.nextDouble()
					* (MINION_SPAWN_RADIUS_MAX - MINION_SPAWN_RADIUS_MIN);
			double x = anchorPos.x + Math.cos(angle) * radius;
			double z = anchorPos.z + Math.sin(angle) * radius;
			double y = anchorPos.y;
			Vec3 minionPos = new Vec3(x, y, z);

			if (random.nextFloat() < MINION_ZOMBIE_FRACTION) {
				spawnZombieMinion(level, minionPos, raidId, huntId);
			} else {
				spawnSkeletonMinion(level, minionPos, raidId, huntId);
			}
		}

		if (huntId != null && target != null) {
			anchor.setTarget(target);
		}
	}

	@Nullable
	private static Zombie spawnAnchor(ServerLevel level, Vec3 pos, UUID raidId, @Nullable UUID huntId) {
		Zombie anchor = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (anchor == null) {
			return null;
		}
		anchor.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);

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
		anchor.setCustomNameVisible(true);
		anchor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_HOE));
		anchor.setDropChance(EquipmentSlot.MAINHAND, 0.0f);

		level.addFreshEntity(anchor);
		RaidBossBars.createFor(anchor);
		return anchor;
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

	/** Вызывается при смерти якоря: Slowness прислугам, сброс GPS и цели. */
	public static void disbandRaidNear(ServerLevel level, UUID raidId, Vec3 center, @Nullable UUID anchorEntityUuid) {
		RaidRegistry.unregister(raidId);
		if (anchorEntityUuid != null) {
			RaidBossBars.remove(anchorEntityUuid);
		}

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
