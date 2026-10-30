package com.example;

import com.example.bastion.Bastion;
import com.example.ritual.RitualBand;
import com.example.skeleton.BasiliskSkeleton;
import com.example.skeleton.TacticianSkeleton;
import com.example.skeleton.WitherSkeletonSword;
import com.example.zombie.ZombieExcalibur;
import com.example.zombie.ZombieMjolnir;
import com.example.zombie.ZombieSorcerer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Опыт за убийство любого враждебного моба.
 * Обычные 5 становятся 10. Броня и оружие в игре сами добавляют ещё 1–3 за вещь:
 * эта добавка тоже умножается.
 *
 * <p>Враждебный — тот, кого игра считает монстром: зомби, скелет, крипер, паук,
 * эндермен, ведьма, брут, слизень, магмовый куб, фантом, блейз и остальные из этого списка.
 * Коровы, свиньи и жители сюда не входят.
 *
 * <p>Срабатывает в момент смерти, когда игра уже посчитала свой опыт.
 * Свой опыт не трогаем: Excalibur, Mjölnir, чародей, ритуал, василиск, Бастион, скелет «700 IQ»,
 * скелет-иссушитель.
 */
public final class MobExperience {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Включить механику. false — опыт как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Во сколько раз больше опыта. 2 = вдвое: голый зомби даёт 10 вместо 5.
	 * 1 = как без мода. 3 = втрое, голый даёт 15.
	 * ФИКС: от дня и шкалы угрозы не растёт. Здоровье моба на это число не влияет.
	 */
	public static final int MULTIPLIER = 2;

	// ========================================================================

	private MobExperience() {
	}

	/**
	 * Сколько опыта оставить. -1 = не враждебный или у него своё число, пусть решает игра.
	 *
	 * @param vanilla уже посчитанный игрой опыт, вместе с добавкой за вещи
	 */
	public static int reward(Mob mob, int vanilla) {
		if (!ENABLED || MULTIPLIER == 1 || vanilla <= 0 || !getsBonus(mob)) {
			return -1;
		}
		long scaled = (long) vanilla * MULTIPLIER;
		if (scaled > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) scaled;
	}

	private static boolean getsBonus(Mob mob) {
		return mob.getType().getCategory() == MobCategory.MONSTER && !hasOwnReward(mob);
	}

	/** У этих мобов опыт уже записан своим числом. Множитель его не увеличивает. */
	private static boolean hasOwnReward(Mob mob) {
		if (mob instanceof Zombie zombie) {
			if (ZombieExcalibur.isExcalibur(zombie)
					|| ZombieMjolnir.isMjolnir(zombie)
					|| ZombieSorcerer.isSorcerer(zombie)) {
				return true;
			}
		}
		return RitualBand.isRitual(mob)
				|| BasiliskSkeleton.isBasilisk(mob)
				|| Bastion.isBastion(mob)
				|| TacticianSkeleton.experienceReward(mob) >= 0
				|| WitherSkeletonSword.experienceReward(mob) >= 0;
	}
}
