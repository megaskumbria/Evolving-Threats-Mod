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
 Experience for killing any hostile mob.
 The normal 5 becomes 10. Armor and weapons in the game add another 1-3 per item:
 * This supplement is also multiplied.
 *
 Hostile - the one whom the game considers a monster: zombie, skeleton, cryper, spider,
 *Enderman, witch, brute, slug, magma cube, phantom, blaze and the rest of this list.
 Cows, pigs and residents are not included.
 *
 <p>It works at the time of death, when the game has already counted its experience.
 We do not touch our experience: Excalibur, Mjölnir, sorcerers, ritual, basilisk, Bastion, skeleton "700 IQ",
 * exhumerator skeleton.
 */
public final class MobExperience {

	// ===================== SETTINGS (change these numbers) =====================

	/** Turn on the mechanics. False - experience as in a normal game. */
	public static final boolean ENABLED = true;

	/**
	 * How many times more experience. 2 = double: naked zombie gives 10 instead of 5.
	 * 1 = like no fashion. 3 = three, naked gives 15.
	 FIXED: From day to day and the scale of threat does not increase. The health of the mob does not affect this number.
	 */
	public static final int MULTIPLIER = 2;

	// ========================================================================

	private MobExperience() {
	}

	/**
	 How much experience to leave. -1 = not hostile or has its own number, let the game decide.
	 *
	 * @param vanilla already considered game experience, along with extra for things
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

	/*In these mobs, the experience is already recorded in its number. The multiplier doesn't increase it. */
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
