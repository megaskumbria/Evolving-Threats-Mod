package com.example.spider;

import com.example.entity.SpiderRoleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Два редких варианта обычного паука (пещерные пауки и пауки-наездники не участвуют):
 * <ul>
 *   <li><b>Ткач</b> — ставит паутину под ноги игроку (цель {@link com.example.entity.ai.SpiderWebWeaveGoal});</li>
 *   <li><b>Матка</b> — паук с 40 HP, при появлении рядом спавнятся 1–4 пещерных паука.</li>
 * </ul>
 * Одним броском при спавне: матка, иначе ткач, иначе рой ({@link SpiderSwarm}), иначе обычный паук.
 * Паутина ткача не исчезает сама.
 * Паук, призванный другим мобом ({@link EntitySpawnReason#MOB_SUMMONED}), остаётся обычным:
 * иначе помощники роя сами открывали бы новые рои.
 */
public final class SpiderThreats {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Включить механику вообще. false — все пауки обычные. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что обычный паук при спавне окажется ТКАЧОМ. 0.10 = 10%.
	 * Бросок общий с маткой: шанс матки проверяется первым.
	 */
	public static final float WEAVER_SPAWN_CHANCE = 0.10f;

	/** Шанс (0.0–1.0), что паук окажется МАТКОЙ. 0.04 = 4% (редко). */
	public static final float MATRIARCH_SPAWN_CHANCE = 0.04f;

	// --- Ткач ---

	/** Пауза между паутинами: минимум и максимум, секунды (случайное значение между ними). */
	public static final int WEB_COOLDOWN_MIN_SECONDS = 5;
	public static final int WEB_COOLDOWN_MAX_SECONDS = 9;

	/** Если не вышло поставить паутину (клетка занята), повторить через столько секунд. */
	public static final int WEB_RETRY_SECONDS = 1;

	/** Дальность (блоки), на которой ткач плюёт паутиной в игрока. Нужна прямая видимость. */
	public static final double WEB_MAX_DISTANCE = 12.0;

	/**
	 * Уважать игровое правило mobGriefing (/gamerule mobGriefing false):
	 * true — если правило выключено, ткач паутину не ставит.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// --- Матка ---

	/** Здоровье матки (у обычного паука 16). 40 = 20 сердец. */
	public static final float MATRIARCH_MAX_HEALTH = 40.0f;

	/** Сколько пещерных пауков появляется рядом при спавне матки (включительно). */
	public static final int MATRIARCH_CAVE_SPIDERS_MIN = 1;
	public static final int MATRIARCH_CAVE_SPIDERS_MAX = 4;

	/** Радиус (блоки) вокруг матки, где появляются пещерные пауки. */
	public static final double MATRIARCH_MINION_RADIUS = 2.5;

	/** Размер матки: 1.0 = как обычный паук, 1.3 = заметно крупнее. Чтобы отличать в бою. */
	public static final double MATRIARCH_SCALE = 1.3;

	// ========================================================================

	public static final int ROLE_NONE = 0;
	public static final int ROLE_WEAVER = 1;
	public static final int ROLE_MATRIARCH = 2;

	private SpiderThreats() {
	}

	public static int getRole(Mob mob) {
		return mob instanceof SpiderRoleEntity data ? data.evolvingthreats$getSpiderRole() : ROLE_NONE;
	}

	public static boolean isWeaver(Mob mob) {
		return ENABLED && getRole(mob) == ROLE_WEAVER;
	}

	/** Вызывается из finalizeSpawn паука (один раз при появлении моба в мире). */
	public static void rollOnSpawn(Spider spider, ServerLevelAccessor level, EntitySpawnReason spawnReason) {
		if (!ENABLED || !(spider instanceof SpiderRoleEntity data) || !isEligible(spider)) {
			return;
		}
		// Помощники роя и прочие призванные пауки не становятся маткой, ткачом и новым роем.
		if (spawnReason == EntitySpawnReason.MOB_SUMMONED) {
			return;
		}
		float roll = spider.getRandom().nextFloat();
		if (roll < MATRIARCH_SPAWN_CHANCE) {
			// Без настоящего серверного мира (генерация чанка) матку не делаем — иначе она будет без пауков.
			if (level instanceof ServerLevel serverLevel) {
				data.evolvingthreats$setSpiderRole(ROLE_MATRIARCH);
				applyMatriarch(spider, serverLevel);
			}
		} else if (roll < MATRIARCH_SPAWN_CHANCE + WEAVER_SPAWN_CHANCE) {
			data.evolvingthreats$setSpiderRole(ROLE_WEAVER);
		} else if (SpiderSwarm.ENABLED
				&& level instanceof ServerLevel serverLevel
				&& roll < MATRIARCH_SPAWN_CHANCE + WEAVER_SPAWN_CHANCE + SpiderSwarm.SPAWN_CHANCE) {
			// Генерация чанка тоже пропускается: там нет мира, куда ставить пачку.
			SpiderSwarm.spawnPack(spider, serverLevel);
		}
	}

	/** Обычный паук: не пещерный и не «наездник» (скелет сверху / сам под всадником). */
	private static boolean isEligible(Spider spider) {
		return !(spider instanceof CaveSpider) && !spider.isVehicle() && !spider.isPassenger();
	}

	private static void applyMatriarch(Spider spider, ServerLevel level) {
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MATRIARCH_MAX_HEALTH);
		spider.setHealth(MATRIARCH_MAX_HEALTH);
		spider.getAttribute(Attributes.SCALE).setBaseValue(MATRIARCH_SCALE);

		int count = spider.getRandom().nextIntBetweenInclusive(MATRIARCH_CAVE_SPIDERS_MIN, MATRIARCH_CAVE_SPIDERS_MAX);
		for (int i = 0; i < count; i++) {
			spawnCaveSpider(level, spider);
		}
	}

	private static void spawnCaveSpider(ServerLevel level, Spider matriarch) {
		CaveSpider minion = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (minion == null) {
			return;
		}
		Vec3 pos = findMinionPos(level, matriarch);
		minion.snapTo(pos.x, pos.y, pos.z, matriarch.getRandom().nextFloat() * 360.0f, 0.0f);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(minion.blockPosition());
		minion.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, (SpawnGroupData) null);
		level.addFreshEntity(minion);
	}

	private static Vec3 findMinionPos(ServerLevel level, Spider matriarch) {
		var dims = EntityTypes.CAVE_SPIDER.getDimensions();
		float half = dims.width() / 2.0f;
		for (int attempt = 0; attempt < 8; attempt++) {
			double x = matriarch.getX() + (matriarch.getRandom().nextDouble() * 2.0 - 1.0) * MATRIARCH_MINION_RADIUS;
			double z = matriarch.getZ() + (matriarch.getRandom().nextDouble() * 2.0 - 1.0) * MATRIARCH_MINION_RADIUS;
			double y = matriarch.getY();
			AABB box = new AABB(x - half, y, z - half, x + half, y + dims.height(), z + half);
			if (level.noCollision(box)) {
				return new Vec3(x, y, z);
			}
		}
		return matriarch.position();
	}

	public static int randomWebCooldownTicks(net.minecraft.util.RandomSource random) {
		int min = WEB_COOLDOWN_MIN_SECONDS * 20;
		int max = WEB_COOLDOWN_MAX_SECONDS * 20;
		return max <= min ? min : random.nextIntBetweenInclusive(min, max);
	}

	/** Разрешено ли ставить паутину по правилу mobGriefing. */
	public static boolean webPlacementAllowed(ServerLevel level) {
		return !RESPECT_MOB_GRIEFING || level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/**
	 * Ставит паутину в клетку, где стоят ноги цели (если клетка пустая или заменяемая, не вода).
	 *
	 * @return true, если паутина поставлена
	 */
	public static boolean placeWebUnderTarget(ServerLevel level, LivingEntity target) {
		BlockPos pos = target.blockPosition();
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.COBWEB)) {
			return false;
		}
		boolean free = state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
		if (!free) {
			return false;
		}
		BlockState web = Blocks.COBWEB.defaultBlockState();
		level.setBlockAndUpdate(pos, web);
		level.playSound(null, pos, web.getSoundType().getPlaceSound(),
				net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.0f);
		// Паутина остаётся навсегда: убрать можно мечом, ножницами или сломав блок.
		return true;
	}
}
