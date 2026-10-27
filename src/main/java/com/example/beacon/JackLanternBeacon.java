package com.example.beacon;

import com.example.threat.ThreatScale;

import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.light.LightSeeking;
import com.example.spawnerhead.SpawnerHeadMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Редкий зомби или скелет с {@link Items#JACK_O_LANTERN} на голове — «маяк».
 * Зомби: не ищет/не ломает свет; раз в несколько тиков ускоряет зомби вокруг к игроку, которого видит.
 * Скелет: раз в кулдаун одна огненная стрела (цель {@link com.example.entity.ai.JackLanternFireShotGoal}).
 */
public final class JackLanternBeacon {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — тыква-фонарь на голове не появляется. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) при спавне стать носителем на 100-й день (РАСТЁТ: в начале игры в 10 раз меньше,
	 * см. ThreatTable.DANGEROUS_START_RATIO). 0.05 = 5%.
	 * Бросок после спавнера (и раздатчика у скелета): голова одна.
	 */
	public static final float CARRIER_SPAWN_CHANCE = 0.05f;

	/** Шанс дропа jack o'lantern с головы при смерти. */
	public static final float JACK_HEAD_DROP_CHANCE = 0.085f;

	// --- Зомби-маяк ---

	/**
	 * Радиус (блоки) вокруг носителя: зомби внутри получают ускорение, если маяк видит игрока.
	 * 20 ≈ как «агро» вокруг факела-маяка.
	 */
	public static final double BEACON_RADIUS = 20.0;

	/** Каждые N тиков (20 = 1 сек) маяк «пульсирует» эффект по стае. */
	public static final int BEACON_PULSE_INTERVAL_TICKS = 20;

	/**
	 * Эффект скорости союзникам: 0 = Speed I, 1 = Speed II.
	 * Длительность должна быть ≥ интервала пульса, иначе эффект будет мигать.
	 */
	public static final int ALLY_SPEED_AMPLIFIER = 0;

	/** Сколько тиков держится Speed у союзника после пульса (40 = 2 сек). */
	public static final int ALLY_SPEED_DURATION_TICKS = 40;

	/**
	 * Если у зомби в радиусе нет цели — дать цель того же игрока, которого видит маяк.
	 * false — только ускорение, без нового агро.
	 */
	public static final boolean SHARE_TARGET_WITH_ALLIES = true;

	// --- Скелет-маяк (огненная стрела) ---

	/** Минимальная пауза между огненными выстрелами (сек). */
	public static final int FIRE_SHOT_COOLDOWN_MIN_SECONDS = 8;

	/** Максимальная пауза между огненными выстрелами (сек). */
	public static final int FIRE_SHOT_COOLDOWN_MAX_SECONDS = 14;

	/** Скорость огненной стрелы. */
	public static final float FIRE_ARROW_VELOCITY = 1.25f;

	/** Разброс (меньше, чем у раздатчика — один точный «жаркий» выстрел). */
	public static final float FIRE_ARROW_INACCURACY = 6.0f;

	/** Секунды горения стрелы после попадания. */
	public static final int FIRE_ARROW_FIRE_SECONDS = 5;

	/** Минимальная дистанция до цели для выстрела (блоки). */
	public static final double FIRE_SHOT_MIN_DISTANCE = 4.0;

	/** Максимальная дистанция до цели для выстрела (блоки). */
	public static final double FIRE_SHOT_MAX_DISTANCE = 22.0;

	// ========================================================================

	private JackLanternBeacon() {
	}

	public static boolean hasJackOnHead(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.JACK_O_LANTERN);
	}

	/** Носитель-зомби с активной ролью маяка (для целей ИИ). */
	public static boolean isBeaconZombie(Zombie zombie) {
		return ENABLED && hasJackOnHead(zombie) && isEligibleSpecial(zombie);
	}

	/** Маяк не гоняется за факелами — см. {@link LightSeeking#isEligible}. */
	public static boolean skipsLightSeeking(Zombie zombie) {
		return isBeaconZombie(zombie);
	}

	public static boolean tryEquipOnSpawn(Mob mob) {
		if (!ENABLED || !isEligibleType(mob) || !isEligibleSpecial(mob)) {
			return false;
		}
		if (SpawnerHeadMob.hasSpawnerOnHead(mob)) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= ThreatScale.dangerousChance(mob.level(), CARRIER_SPAWN_CHANCE)) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
		mob.setDropChance(EquipmentSlot.HEAD, JACK_HEAD_DROP_CHANCE);
		return true;
	}

	public static int randomFireShotCooldownTicks(net.minecraft.util.RandomSource random) {
		int min = FIRE_SHOT_COOLDOWN_MIN_SECONDS * 20;
		int max = FIRE_SHOT_COOLDOWN_MAX_SECONDS * 20;
		if (max <= min) {
			return min;
		}
		return random.nextIntBetweenInclusive(min, max);
	}

	/** Одна «волна» ускорения стаи — вызывается из {@link JackBeaconAuraGoal}. */
	public static void pulseAura(Zombie beacon, LivingEntity focus) {
		if (!ENABLED || !(beacon.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(focus instanceof Player)) {
			return;
		}
		AABB area = beacon.getBoundingBox().inflate(BEACON_RADIUS);
		for (Zombie ally : level.getEntitiesOfClass(Zombie.class, area, z -> canReceiveBeaconBuff(z, beacon))) {
			ally.addEffect(new MobEffectInstance(
					MobEffects.SPEED,
					ALLY_SPEED_DURATION_TICKS,
					ALLY_SPEED_AMPLIFIER,
					false,
					false,
					true));
			if (SHARE_TARGET_WITH_ALLIES && ally.getTarget() == null) {
				ally.setTarget(focus);
			}
		}
	}

	private static boolean canReceiveBeaconBuff(Zombie ally, Zombie beacon) {
		if (ally == beacon || !ally.isAlive()) {
			return false;
		}
		if (ally instanceof Drowned || ally instanceof ZombifiedPiglin) {
			return false;
		}
		if (ally instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (ally instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		return true;
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
