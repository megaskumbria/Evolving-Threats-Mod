package com.example.phantom;

import com.example.entity.BlindingPhantomEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 * Часть фантомов вешает слепоту, когда укус снял здоровье.
 * Кто умеет — решается один раз при появлении, отдельно от слабости.
 * Один фантом может уметь и то и другое, только одно или ничего.
 * Щит, который отбил удар, и короткое мигание после удара слепоту не дают.
 * Шанс не растёт с днями: какой записан ниже, такой и остаётся.
 */
public final class PhantomBlindness {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — укус снова без слепоты, даже у уже помеченных фантомов. Слабость при этом остаётся. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что фантом при появлении умеет вешать слепоту.
	 * 0.20 = 20%, примерно каждый пятый. Бросок один и отдельно от слабости:
	 * ночная стая, яйцо и команда спавна.
	 * Такой фантом слепит каждым укусом, который прошёл. Остальные — никогда.
	 * От дней мира не зависит. 0 = слепоты нет. 1 = её вешает каждый фантом.
	 */
	public static final float SPAWN_CHANCE = 0.20f;

	/**
	 * Слепота, секунды. Случайное число от меньшего до большего включительно.
	 * 7 и 12 = от 7 до 12 секунд. Экран темнеет, фантома плохо видно.
	 * Повторный укус обновляет время, если на игроке слепота уже короче или такая же.
	 * Более долгая слепота (например от зелья) этим укусом не укорачивается.
	 * Молоко снимает эффект сразу. Оба числа 0 или меньше = эффект не появится.
	 */
	public static final int BLINDNESS_MIN_SECONDS = 7;
	public static final int BLINDNESS_MAX_SECONDS = 12;

	// ========================================================================

	private PhantomBlindness() {
	}

	/** Один раз при появлении фантома. Повторный вызов уже помеченного не перебрасывает шанс. */
	public static void rollOnSpawn(Phantom phantom) {
		if (!ENABLED || SPAWN_CHANCE <= 0.0f || !(phantom instanceof BlindingPhantomEntity data)) {
			return;
		}
		if (data.evolvingthreats$blindsOnBite()) {
			return;
		}
		if (phantom.getRandom().nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		data.evolvingthreats$setBlindsOnBite(true);
	}

	/**
	 * Укус уже снял здоровье.
	 * Не игрок сюда не попадает: голем, животное и другой моб слепоту не получают.
	 */
	public static void onBite(Phantom phantom, LivingEntity target) {
		if (!ENABLED || !(target instanceof Player)) {
			return;
		}
		if (!(phantom instanceof BlindingPhantomEntity data) || !data.evolvingthreats$blindsOnBite()) {
			return;
		}
		int seconds = rollSeconds(phantom);
		if (seconds <= 0) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.BLINDNESS,
				seconds * 20,
				0,
				false,
				true,
				true), phantom);
	}

	/** Меньшее и большее из двух настроек. Если оба не больше нуля — слепоты нет. */
	private static int rollSeconds(Phantom phantom) {
		int low = Math.min(BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS);
		int high = Math.max(BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS);
		if (high <= 0) {
			return 0;
		}
		if (low < 0) {
			low = 0;
		}
		if (high <= low) {
			return high;
		}
		return low + phantom.getRandom().nextInt(high - low + 1);
	}
}
