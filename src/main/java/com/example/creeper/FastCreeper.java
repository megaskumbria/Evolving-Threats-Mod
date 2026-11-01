package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.FastCreeperEntity;
import com.example.entity.FeintCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.mixin.CreeperFuseAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Редкий крипер, от которого нельзя убежать.
 * Бежит заметно быстрее спринта: по прямой догоняет и игрока, и прыжки в спринте.
 * Шипит полсекунды и взрывается. У обычного крипера шипение 1,5 секунды.
 * Подрывником, метателем динамита и обманкой не бывает: у тех свой способ добраться до игрока.
 */
public final class FastCreeper {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Включить механику. false — такие криперы не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный крипер окажется супербыстрым. 0.03 = 3%.
	 * FIXED: с первого дня, от шкалы угрозы и луны не зависит.
	 * Это стартовое число. У подрывника и ломателя щита 5%, у заряженного 1%.
	 * 0 = только команда. 1 = каждый крипер такой.
	 * Команда этот шанс не бросает.
	 */
	public static final float SPAWN_CHANCE = 0.03f;

	/**
	 * Скорость погони. У обычного крипера 0.25 — спринт игрока от него уходит.
	 * 0.50 = вдвое быстрее обычного: спринт и прыжки в спринте его не обгоняют, по прямой он догоняет.
	 * 0.35 — на ровном поле ещё можно оторваться. 0.70 — почти не увернуться.
	 */
	public static final double CHASE_SPEED = 0.50;

	/**
	 * Как далеко (блоки) помнит игрока и продолжает бежать.
	 * У обычного крипера около 16: выбежал из этой зоны — он бросает погоню.
	 * 48 = убежать «за угол видимости» не выйдет, он всё равно догоняет.
	 * 16 = как у обычного. 0 = почти сразу теряет цель.
	 */
	public static final double FOLLOW_RANGE = 48.0;

	/**
	 * Сколько секунд шипит, прежде чем взорваться. 0.5 = полсекунды.
	 * У обычного крипера 1.5. Сила взрыва та же.
	 * 0 и меньше игра считает как один миг, иначе взрыв случится в тот же тик, что и шипение.
	 */
	public static final float FUSE_SECONDS = 0.5f;

	// ========================================================================

	private FastCreeper() {
	}

	public static boolean isFast(Creeper creeper) {
		return ENABLED
				&& creeper instanceof FastCreeperEntity data
				&& data.evolvingthreats$isFastCreeper();
	}

	/** Конец появления крипера. Командный спавн шанс не бросает: флаг ставят заранее. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof FastCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isFastCreeper()) {
			apply(creeper);
			return;
		}
		// Подрывник и метатель догоняют по-своему. Обманка быстрая только вблизи. Скорость им не подменяем.
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof FeintCreeperEntity feint && feint.evolvingthreats$isFeintCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		apply(creeper);
	}

	/** Скорость, дальность погони и короткое шипение. Вызывать и при появлении, и когда крипер загрузился из мира. */
	public static void apply(Creeper creeper) {
		if (!(creeper instanceof FastCreeperEntity data)) {
			return;
		}
		data.evolvingthreats$setFastCreeper(true);
		var speed = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(Math.max(0.0, CHASE_SPEED));
		}
		var follow = creeper.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(Math.max(1.0, FOLLOW_RANGE));
		}
		if (creeper instanceof CreeperFuseAccessor fuse) {
			fuse.evolvingthreats$setMaxSwell(fuseTicks());
		}
	}

	/** Тики шипения. 20 тиков = 1 секунда. 0.5 секунды = 10 тиков. */
	public static int fuseTicks() {
		return Math.max(1, Math.round(FUSE_SECONDS * 20.0f));
	}

	/** Команда: быстрый крипер перед игроком. null — механика выключена. */
	public static @Nullable Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create fast creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof FastCreeperEntity data) {
			data.evolvingthreats$setFastCreeper(true);
		}
		creeper.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(creeper.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		apply(creeper);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
