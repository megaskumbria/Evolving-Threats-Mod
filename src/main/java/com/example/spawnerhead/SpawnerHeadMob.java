package com.example.spawnerhead;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.SpawnerHeadCarrierEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
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

/**
 * Редкий зомби или скелет с {@link Items#SPAWNER} на голове.
 * Каждые {@link #HITS_TO_SUMMON} удара игрока призывает {@link #SUMMON_COUNT_MIN}–{@link #SUMMON_COUNT_MAX}
 * таких же мобов рядом (тот же тип). Подмога никогда не получает спавнер на голове.
 */
public final class SpawnerHeadMob {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — спавнер на голове не появляется. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что при спавне моб станет носителем. 0.04 = 4% (редко).
	 * Отдельный бросок для каждого зомби/скелета.
	 */
	public static final float CARRIER_SPAWN_CHANCE = 0.04f;

	/** Сколько ударов игрока нужно, чтобы сработал призыв (2 = два попадания). */
	public static final int HITS_TO_SUMMON = 2;

	/** Минимум и максимум призванных мобов за один срабатывание (включительно). */
	public static final int SUMMON_COUNT_MIN = 1;
	public static final int SUMMON_COUNT_MAX = 2;

	/** Радиус (блоки) вокруг носителя, где появляются прислужники. */
	public static final double SUMMON_RADIUS = 3.0;

	/** Шанс дропа спавнера с головы при смерти (как у брони). */
	public static final float SPAWNER_HEAD_DROP_CHANCE = 0.085f;

	// ========================================================================

	private SpawnerHeadMob() {
	}

	public static boolean hasSpawnerOnHead(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.SPAWNER);
	}

	/** Вызвать при finalizeSpawn зомби/скелета (после брони). Возвращает true, если надел спавнер. */
	public static boolean tryEquipOnSpawn(Mob mob) {
		if (!ENABLED || !isEligibleType(mob) || !isEligibleSpecial(mob)) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= CARRIER_SPAWN_CHANCE) {
			return false;
		}
		if (!(mob instanceof SpawnerHeadCarrierEntity carrier)) {
			return false;
		}
		// Призванная подмога — только обычная экипировка, без цепочки «спавнер на голове».
		if (carrier.evolvingthreats$isSpawnerReinforcement()) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SPAWNER));
		mob.setDropChance(EquipmentSlot.HEAD, SPAWNER_HEAD_DROP_CHANCE);
		carrier.evolvingthreats$setSpawnerHeadCarrier(true);
		carrier.evolvingthreats$setSpawnerHeadHitCount(0);
		return true;
	}

	/** После успешного урона по носителю (вызывается из mixin). */
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
	}

	private static void summonMinions(Mob carrier, ServerLevel level) {
		EntityType<? extends Mob> type = getSummonType(carrier);
		if (type == null) {
			return;
		}
		int count = carrier.getRandom().nextIntBetweenInclusive(SUMMON_COUNT_MIN, SUMMON_COUNT_MAX);
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
