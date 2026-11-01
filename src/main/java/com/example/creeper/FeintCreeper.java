package com.example.creeper;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.FastCreeperEntity;
import com.example.entity.FeintCreeperEntity;
import com.example.entity.SapperCreeperEntity;
import com.example.mixin.CreeperFuseAccessor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Обычный с виду крипер, но вокруг него всегда кружатся частицы зелья скорости.
 * По ним его узнают заранее: после рывка реагировать уже некогда.
 * Частицы — только картинка, само зелье не даётся и быстрее от них он не бежит.
 * Пока игрок далеко, скорость и шипение как у ванильного.
 * Где-то за 6–7 блоков маскировка срывается: он бежит как супербыстрый и шипит полсекунды.
 * Ника нет. Подрывником, метателем динамита и супербыстрым сразу не бывает.
 * Заряженным и ломателем щита быть может.
 */
public final class FeintCreeper {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — такие криперы не появляются, команда тоже не спавнит. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный крипер окажется обманкой. 0.003 = 0,3%.
	 * ФИКС: с первого дня, от шкалы угрозы и луны не зависит.
	 * Реже супербыстрого (у того 3%). 0 = только команда. 1 = каждый крипер такой.
	 * Команда этот шанс не бросает.
	 */
	public static final float SPAWN_CHANCE = 0.003f;

	/**
	 * Ближний край зоны, где маскировка срывается. В блоках.
	 * Каждый такой крипер один раз выбирает свою дистанцию между этим числом и TRIGGER_MAX.
	 * 6 = рывок почти вплотную. Меньше 3 бессмысленно: обычный крипер и так начинает шипеть около 3 блоков.
	 */
	public static final float TRIGGER_MIN = 6.0f;

	/**
	 * Дальний край зоны срыва. В блоках. 7 = как граница, на которой обычный крипер ещё не тушит шипение.
	 * 6 и 7 вместе: «где-то за 6–7 блоков». Оба равны — дистанция всегда одна и та же.
	 */
	public static final float TRIGGER_MAX = 7.0f;

	/**
	 * Скорость после срыва. У обычного крипера 0.25 — спринт от него уходит.
	 * 0.50 = как у супербыстрого: по прямой догоняет и спринт, и прыжки в спринте.
	 * Меняй только здесь. Супербыстрый (FastCreeper.CHASE_SPEED) от этого числа не зависит.
	 */
	public static final double CHASE_SPEED = 0.50;

	/**
	 * Скорость, пока маскировка держится. 0.25 = обычный крипер.
	 * Пока игрок дальше зоны срыва, обманка от него не отличается.
	 */
	public static final double CALM_SPEED = 0.25;

	/**
	 * Сколько секунд шипит после срыва. 0.5 = полсекунды, как у супербыстрого.
	 * Сила взрыва та же. За полсекунды отбежать почти не успеть.
	 * 0 и меньше игра считает как один миг.
	 */
	public static final float FUSE_SECONDS = 0.5f;

	/**
	 * Сколько секунд шипит, пока притворяется обычным. 1.5 = ванильный крипер.
	 * Огниво издалека, до срыва маскировки, даёт это длинное шипение.
	 */
	public static final float CALM_FUSE_SECONDS = 1.5f;

	/**
	 * true — после первого срыва он уже не притворяется: быстрый и с коротким шипением до конца жизни,
	 * даже если игрок отбежал. Отойти на шаг и снова сделать его медленным нельзя.
	 * false — быстрый только пока игрок внутри зоны 6–7 блоков. Отошёл дальше и шипение погасло —
	 * снова обычный, следующая встреча опять обманка.
	 */
	public static final boolean KEEP_BURST = true;

	/**
	 * Частицы зелья скорости вокруг обманки. true — видны всё время, и пока она ещё идёт медленно.
	 * Так её можно узнать до рывка. false — снова неотличима от обычного крипера.
	 * Это не зелье: скорость остаётся {@link #CALM_SPEED}, пока маскировка не сорвалась.
	 * Отдельное зелье скорости при появлении (MobPotionBuffs) по-прежнему может ускорить рывок.
	 */
	public static final boolean SPEED_PARTICLES = true;

	/**
	 * Как часто сыпать одну частицу, в тиках. У моба с выпитым зельем игра делает это
	 * примерно раз в 4 тика — издалека почти не разглядеть. 2 = чуть гуще, обманку видно заранее.
	 * 1 = каждый тик, облако плотнее. 0 и меньше — частиц нет, как при SPEED_PARTICLES = false.
	 */
	public static final int SPEED_PARTICLE_EVERY_TICKS = 2;

	// ========================================================================

	/** Цвет и вид частицы — те же, что у выпитого зелья скорости. Считается один раз. */
	private static ParticleOptions speedParticle;

	private FeintCreeper() {
	}

	public static boolean isFeint(Creeper creeper) {
		return ENABLED
				&& creeper instanceof FeintCreeperEntity data
				&& data.evolvingthreats$isFeintCreeper();
	}

	/** Конец появления крипера. Командный спавн шанс не бросает: флаг ставят заранее. */
	public static void rollOnSpawn(Creeper creeper, EntitySpawnReason reason) {
		if (!ENABLED || !(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$isFeintCreeper()) {
			ensureDistance(creeper);
			return;
		}
		// Подрывник и метатель догоняют по-своему. Супербыстрый быстрый сразу, маскировки у него нет.
		if (creeper instanceof SapperCreeperEntity sapper && sapper.evolvingthreats$isSapperCreeper()) {
			return;
		}
		if (creeper instanceof DynamiteCreeperEntity dynamite && dynamite.evolvingthreats$isDynamiteCreeper()) {
			return;
		}
		if (creeper instanceof FastCreeperEntity fast && fast.evolvingthreats$isFastCreeper()) {
			return;
		}
		if (reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		if (SPAWN_CHANCE <= 0.0f || creeper.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setFeintCreeper(true);
		ensureDistance(creeper);
	}

	/**
	 * Каждый тик на сервере. Далеко — обычные скорость и шипение.
	 * Игрок вошёл в зону — рывок и шипение полсекунды.
	 * Шипение по-прежнему начинается вплотную (около 3 блоков), как у обычного крипера:
	 * на 6–7 блоках взрыв ещё не достаёт, поэтому там только разгон.
	 */
	public static void tick(Creeper creeper) {
		if (!isFeint(creeper) || creeper.level().isClientSide()) {
			return;
		}
		if (!(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		ensureDistance(creeper);
		boolean close = playerInTrigger(creeper, data.evolvingthreats$getTriggerDistance());
		if (close) {
			data.evolvingthreats$setFeintRevealed(true);
		}
		if (shouldBurst(creeper, data, close)) {
			applyBurst(creeper);
		} else {
			applyCalm(creeper);
		}
		spawnSpeedParticles(creeper);
	}

	/** Команда: обманка перед игроком. Пока до зоны далеко, идёт как обычный. null — механика выключена. */
	public static @Nullable Creeper spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt) {
		if (!ENABLED) {
			return null;
		}
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			throw new IllegalStateException("Failed to create feint creeper");
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		creeper.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		if (creeper instanceof FeintCreeperEntity data) {
			data.evolvingthreats$setFeintCreeper(true);
			data.evolvingthreats$setTriggerDistance(rollDistance(creeper));
		}
		creeper.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(creeper.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (lookAt != null) {
			creeper.setTarget(lookAt);
		}
		level.addFreshEntity(creeper);
		return creeper;
	}

	private static boolean shouldBurst(Creeper creeper, FeintCreeperEntity data, boolean close) {
		if (KEEP_BURST) {
			return data.evolvingthreats$isFeintRevealed();
		}
		// Без «запомнить рывок»: короткий запал держится, пока шипение не погасло,
		// иначе отбежать на шаг во время шипения растянуло бы его обратно до 1,5 секунды.
		return close || (isHissing(creeper) && isShortFuse(creeper));
	}

	private static void applyBurst(Creeper creeper) {
		setSpeed(creeper, CHASE_SPEED);
		setFuse(creeper, fuseTicks(FUSE_SECONDS));
	}

	private static void applyCalm(Creeper creeper) {
		setSpeed(creeper, CALM_SPEED);
		setFuse(creeper, fuseTicks(CALM_FUSE_SECONDS));
	}

	/**
	 * Те же голубые завитки, что у зелья скорости. Зелье на крипера не вешается,
	 * поэтому спокойная скорость 0.25 от частиц не меняется.
	 * Видно примерно на 32 блока — дальше игра такие частицы не рисует.
	 */
	private static void spawnSpeedParticles(Creeper creeper) {
		if (!SPEED_PARTICLES || SPEED_PARTICLE_EVERY_TICKS <= 0 || !creeper.isAlive()) {
			return;
		}
		if (creeper.tickCount % SPEED_PARTICLE_EVERY_TICKS != 0) {
			return;
		}
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		// count = 0: одна частица точно в этой точке, скорости 1,1,1 — как у зелья на мобе.
		level.sendParticles(
				speedParticle(),
				creeper.getRandomX(0.5),
				creeper.getRandomY(),
				creeper.getRandomZ(0.5),
				0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0);
	}

	private static ParticleOptions speedParticle() {
		if (speedParticle == null) {
			// ambient = false: яркие частицы, как у выпитого зелья, а не тусклые от маяка.
			MobEffectInstance effect = new MobEffectInstance(MobEffects.SPEED, 1, 0, false, true, false);
			speedParticle = MobEffects.SPEED.value().createParticleOptions(effect);
		}
		return speedParticle;
	}

	private static boolean playerInTrigger(Creeper creeper, double blocks) {
		LivingEntity target = creeper.getTarget();
		if (!(target instanceof Player player) || player.isSpectator() || player.isDeadOrDying()) {
			return false;
		}
		double reach = Math.max(0.0, blocks);
		return creeper.distanceToSqr(player) <= reach * reach;
	}

	private static void ensureDistance(Creeper creeper) {
		if (!(creeper instanceof FeintCreeperEntity data)) {
			return;
		}
		if (data.evolvingthreats$getTriggerDistance() <= 0.0) {
			data.evolvingthreats$setTriggerDistance(rollDistance(creeper));
		}
	}

	private static double rollDistance(Creeper creeper) {
		float min = Math.min(TRIGGER_MIN, TRIGGER_MAX);
		float max = Math.max(TRIGGER_MIN, TRIGGER_MAX);
		if (max <= min) {
			return min;
		}
		return Mth.randomBetween(creeper.getRandom(), min, max);
	}

	private static void setSpeed(Creeper creeper, double speed) {
		var attribute = creeper.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute != null && attribute.getBaseValue() != speed) {
			attribute.setBaseValue(Math.max(0.0, speed));
		}
	}

	/**
	 * Длина шипения. Если крипер уже шипит, оставшаяся доля времени сохраняется:
	 * смена запала не взрывает его в тот же тик и не обнуляет шипение.
	 */
	private static void setFuse(Creeper creeper, int ticks) {
		if (!(creeper instanceof CreeperFuseAccessor fuse)) {
			return;
		}
		int currentMax = fuse.evolvingthreats$getMaxSwell();
		if (currentMax == ticks) {
			return;
		}
		int swell = fuse.evolvingthreats$getSwell();
		if (swell > 0 && currentMax > 0) {
			int scaled = Math.round(swell * (ticks / (float) currentMax));
			if (swell < currentMax) {
				scaled = Math.min(scaled, Math.max(0, ticks - 1));
			}
			fuse.evolvingthreats$setSwell(Math.max(0, scaled));
		}
		fuse.evolvingthreats$setMaxSwell(Math.max(1, ticks));
	}

	private static boolean isHissing(Creeper creeper) {
		return creeper instanceof CreeperFuseAccessor fuse && fuse.evolvingthreats$getSwell() > 0;
	}

	private static boolean isShortFuse(Creeper creeper) {
		return creeper instanceof CreeperFuseAccessor fuse
				&& fuse.evolvingthreats$getMaxSwell() <= fuseTicks(FUSE_SECONDS);
	}

	/** Тики шипения. 20 тиков = 1 секунда. 0.5 секунды = 10 тиков. */
	private static int fuseTicks(float seconds) {
		return Math.max(1, Math.round(seconds * 20.0f));
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
