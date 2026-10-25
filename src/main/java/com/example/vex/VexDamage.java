package com.example.vex;

import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;

/**
 * Досаждатель бьёт чуть больнее, чем в обычной игре, и иногда вешает слабость.
 * Прибавка урона одинаковая на лёгкой, нормальной и сложной: игра сама меняет урон по сложности,
 * а здесь в удар кладётся такое число, чтобы после этого осталось ровно столько сердец, сколько задано.
 */
public final class VexDamage {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — удар как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Сколько лишних сердец снимает один удар, без брони.
	 * 1 = одно сердце (2 здоровья). 0 = без прибавки. 2 = два сердца.
	 * На лёгкой и сложной игрок теряет столько же: пересчёт сложности уже учтён.
	 */
	public static final float EXTRA_HEARTS = 1.0f;

	/** false — слабости нет, лишнее сердце остаётся. */
	public static final boolean WEAKNESS_ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что удар, который уже снял здоровье, повесит слабость.
	 * 0.20 = 20%, примерно каждый пятый такой удар. 0.25 = каждый четвёртый.
	 * Бросок заново на каждый удар. Щит, который отбил удар, и мигание после удара не считаются.
	 * 0 = слабости нет. 1 = её вешает каждый прошедший удар.
	 */
	public static final float WEAKNESS_CHANCE = 0.20f;

	/**
	 * Сколько секунд длится слабость. 10 = десять секунд.
	 * Новый удар обновляет время, если на игроке слабость уже короче или такая же.
	 * Более долгая слабость (например от зелья ведьмы) этим ударом не укорачивается.
	 * Молоко снимает эффект сразу. 0 = эффект не появится.
	 */
	public static final int WEAKNESS_SECONDS = 10;

	/**
	 * Сила слабости. 0 = Слабость I: удары игрока вблизи слабее на 4 здоровья (2 сердца).
	 * 1 = Слабость II: слабее на 8 здоровья (4 сердца), меч часто почти не бьёт.
	 */
	public static final int WEAKNESS_AMPLIFIER = 0;

	// ========================================================================

	/** В одном сердце два очка здоровья. */
	private static final float HP_PER_HEART = 2.0f;

	private VexDamage() {
	}

	/**
	 * Урон удара с прибавкой. {@code incoming} — то, что досаждатель и так нанёс бы
	 * (свой урон плюс железный меч и острота, если они есть).
	 */
	public static float attackDamage(Vex vex, float incoming) {
		if (!ENABLED || EXTRA_HEARTS <= 0.0f) {
			return incoming;
		}
		float feltBonus = EXTRA_HEARTS * HP_PER_HEART;
		if (!(vex.getTarget() instanceof Player)) {
			return incoming + feltBonus;
		}
		return incoming + beforeDifficultyScale(vex.level().getDifficulty(), feltBonus);
	}

	/**
	 * Сколько добавить в удар, чтобы после пересчёта сложности осталось {@code feltHp}.
	 * Лёгкая режет большой урон примерно вдвое, сложная умножает на 1,5, нормальная не трогает.
	 */
	private static float beforeDifficultyScale(Difficulty difficulty, float feltHp) {
		return switch (difficulty) {
			case EASY -> feltHp * 2.0f;
			case HARD -> feltHp * 2.0f / 3.0f;
			default -> feltHp;
		};
	}

	/**
	 * Удар уже снял здоровье.
	 * Не игрок сюда не попадает: житель, голем и другой моб слабость не получают.
	 */
	public static void onHit(Vex vex, LivingEntity target) {
		if (!WEAKNESS_ENABLED || WEAKNESS_CHANCE <= 0.0f || WEAKNESS_SECONDS <= 0) {
			return;
		}
		if (!(target instanceof Player)) {
			return;
		}
		if (vex.getRandom().nextFloat() >= WEAKNESS_CHANCE) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS,
				WEAKNESS_SECONDS * 20,
				Math.max(0, WEAKNESS_AMPLIFIER),
				false,
				true,
				true), vex);
	}
}
