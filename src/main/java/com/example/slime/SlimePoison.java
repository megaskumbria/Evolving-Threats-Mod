package com.example.slime;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;

/**
 * Удар слизня может отравить игрока.
 * Большой — Отравление II, средний — Отравление I. Маленький яд не вешает.
 * Магмовый куб сюда не попадает: это не слизень, у него свой удар.
 */
public final class SlimePoison {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить яд. false — удары слизней снова только с уроном, без отравления. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что удар большого слизня повесит Отравление II.
	 * 0.30 = 30%. Бросается один раз за удар, который прошёл (щит и мигание после удара не считают).
	 * 0 = большой слизень яд не вешает.
	 */
	public static final float LARGE_CHANCE = 0.30f;

	/**
	 * Шанс (0.0–1.0), что удар среднего слизня повесит Отравление I.
	 * 0.20 = 20%. 0 = средний слизень яд не вешает.
	 */
	public static final float MEDIUM_CHANCE = 0.20f;

	/**
	 * Сколько секунд длится отравление. 15 = пятнадцать секунд.
	 * И для большого, и для среднего. 0 = эффект не появится.
	 */
	public static final int POISON_SECONDS = 15;

	/**
	 * Размер среднего слизня. 2 = средний, как в обычной игре.
	 * 1 и меньше — маленький: яд не вешает, даже если шанс выше нуля.
	 */
	public static final int MEDIUM_SIZE = 2;

	/**
	 * С этого размера слизень считается большим.
	 * Обычный большой имеет размер 4. 3 и выше тоже получают Отравление II.
	 */
	public static final int LARGE_MIN_SIZE = 3;

	/** Сила эффекта: 0 = Отравление I, 1 = Отравление II. */
	public static final int MEDIUM_AMPLIFIER = 0;

	/** Сила эффекта большого слизня. 1 = Отравление II. */
	public static final int LARGE_AMPLIFIER = 1;

	// ========================================================================

	private SlimePoison() {
	}

	/** Удар уже прошёл. Маленький слизень, магмовый куб и не-игрок сюда не попадают. */
	public static void onHit(Slime slime, LivingEntity target) {
		if (!ENABLED || !(target instanceof Player)) {
			return;
		}
		if (POISON_SECONDS <= 0) {
			return;
		}
		int size = slime.getSize();
		if (size >= LARGE_MIN_SIZE) {
			tryPoison(slime, target, LARGE_CHANCE, LARGE_AMPLIFIER);
		} else if (size == MEDIUM_SIZE) {
			tryPoison(slime, target, MEDIUM_CHANCE, MEDIUM_AMPLIFIER);
		}
	}

	private static void tryPoison(Slime slime, LivingEntity target, float chance, int amplifier) {
		if (chance <= 0.0f || slime.getRandom().nextFloat() >= chance) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.POISON,
				POISON_SECONDS * 20,
				Math.max(0, amplifier),
				false,
				true,
				true), slime);
	}
}
