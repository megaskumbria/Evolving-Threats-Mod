package com.example.skeleton;

import com.example.entity.SkeletonHorsemanMount;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Скелет на костяной лошади.
 * В ванили такая лошадь-ловушка выходит только в грозу и очень редко.
 * Здесь обычный скелет ночью на поверхности иногда сразу садится на лошадь.
 * Наездник — тот же скелет, что и пеший: щит, зелья, стрелы, броня и оружие уже выданы.
 * Грозовая ловушка по-прежнему может выйти. Её наездников игра больше не переодевает:
 * не надевает железный шлем поверх нашего и не стирает чары с лука и шлема.
 */
public final class SkeletonHorseman {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/**
	 * Включить и ночной спавн наездника, и запрет грозовой ловушке переодевать скелета.
	 * false — наездники снова только из грозы, и ловушка одевает их по-ванильному.
	 */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный скелет ночью на поверхности окажется на лошади.
	 * 0.05 = 5%. Бросается после брони и оружия, только в обычном мире и только если над головой небо.
	 * Пещеры, спаунер, ад и грозовая ловушка этот шанс не бросают.
	 */
	public static final float SPAWN_CHANCE = 0.05f;

	/**
	 * Дальше этого расстояния (в блоках) лошадь и всадник исчезают, как обычный скелет.
	 * 128 — то же, что у монстров. 0 — не исчезать, пока их не убьют.
	 * Костяная лошадь сама по себе не пропадает, поэтому без этого мир заполнится наездниками.
	 */
	public static final double DESPAWN_DISTANCE_BLOCKS = 128.0;

	// ========================================================================

	private SkeletonHorseman() {
	}

	/**
	 * Конец появления скелета. Лошадь создаётся здесь, скелет на неё садится.
	 * Если шанс не выпал — скелет остаётся пешим.
	 */
	public static void tryMount(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!canMountNaturally(skeleton, level, reason)) {
			return;
		}
		if (skeleton.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		mount((ServerLevel) level, skeleton);
	}

	/** Команда: уже одетый скелет на лошади. null — механика выключена. */
	public static @Nullable Skeleton spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			throw new IllegalStateException("Failed to create skeleton horseman");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				(SpawnGroupData) null);
		mount(level, skeleton);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return skeleton;
	}

	private static boolean canMountNaturally(AbstractSkeleton skeleton, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (!ENABLED || !(skeleton instanceof Skeleton)) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL || skeleton.isPassenger()) {
			return false;
		}
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
			return false;
		}
		return server.canSeeSky(skeleton.blockPosition());
	}

	/**
	 * Лошадь мода не должна жить вечно. Далеко от игрока пропадают оба.
	 * Если всадник умер, лошадь тоже пропадает: это не питомец.
	 * Игрок успел сесть сам — лошадь остаётся.
	 */
	public static void tickMount(SkeletonHorse horse) {
		if (!ENABLED) {
			return;
		}
		if (!(horse instanceof SkeletonHorsemanMount mount) || !mount.evolvingthreats$isHorseman()) {
			return;
		}
		if (!(horse.level() instanceof ServerLevel level) || horse.isRemoved()) {
			return;
		}
		boolean skeletonRiding = false;
		for (Entity passenger : horse.getPassengers()) {
			if (passenger instanceof Player) {
				return;
			}
			if (passenger instanceof AbstractSkeleton) {
				skeletonRiding = true;
			}
		}
		if (!skeletonRiding) {
			horse.discard();
			return;
		}
		if (DESPAWN_DISTANCE_BLOCKS <= 0.0 || level.getNearestPlayer(horse, DESPAWN_DISTANCE_BLOCKS) != null) {
			return;
		}
		for (Entity passenger : List.copyOf(horse.getPassengers())) {
			passenger.discard();
		}
		horse.discard();
	}

	private static void mount(ServerLevel level, AbstractSkeleton skeleton) {
		SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.JOCKEY);
		if (horse == null) {
			return;
		}
		horse.snapTo(skeleton.getX(), skeleton.getY(), skeleton.getZ(), skeleton.getYRot(), 0.0f);
		horse.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(horse.blockPosition()),
				EntitySpawnReason.JOCKEY,
				(SpawnGroupData) null);
		horse.setTamed(true);
		horse.setAge(0);
		horse.setTrap(false);
		if (horse instanceof SkeletonHorsemanMount mount) {
			mount.evolvingthreats$setHorseman(true);
		}
		skeleton.startRiding(horse);
		level.addFreshEntity(horse);
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
