package com.example.phantom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

/**
 Phantoms bite more painfully than in a normal game, and the pack grows if you do not sleep long.
 The first nights of insomnia are vanilla group size. Then phantoms are added to it.
 */
public final class PhantomThreats {

	// ===================== SETTINGS (change these numbers) =====================

	/*False: The bite and size of the pack as in a normal game. */
	public static final boolean ENABLED = true;

	/**
	 How much health does a bite relieve on a light complexity, without armor.
	 3 = 1.5 hearts. In a normal game 2 (1 heart).
	 */
	public static final float EASY_BITE_HP = 3.0f;

	/**
	 How much health does a bite relieve on normal complexity, without armor.
	 6 = 3 hearts. So the phantom hit when it was first added. In a normal game, it's 2.
	 */
	public static final float NORMAL_BITE_HP = 6.0f;

	/**
	 How much health relieves the bite on complex complexity, without armor.
	 9 = 4.5 hearts. In a normal game, 3 (1.5 hearts).
	 */
	public static final float HARD_BITE_HP = 9.0f;

	/**
	 How many days without a bed is a pack of ordinary size?
	 3 = The first nights when phantoms are just beginning to appear.
	 Less than 3 does not turn off the spawn itself: the game still needs to stay awake for three days.
	 */
	public static final int CALM_DAYS = 3;

	/**
	 For how many extra days without sleep one phantom is added to the flock.
	 2 = on the 5th day +1, on the 7th +2, on the 9th +3. 1 = plus a phantom every day after the third.
	 */
	public static final int DAYS_PER_EXTRA_PHANTOM = 2;

	/**
	 Ceiling a pack in one wave. The wave comes every 1-2 minutes until the player goes to bed.
	 A normal game without this ceiling: easy 1-2, normal 1-3, difficult 1-4.
	 If you put the ceiling below the usual maximum, the pack will become less vanilla.
	 */
	public static final int EASY_FLOCK_CAP = 3;
	public static final int NORMAL_FLOCK_CAP = 5;
	public static final int HARD_FLOCK_CAP = 6;

	// ========================================================================

	/*The bite of a common phantom before the calculation of complexity. From it separates the addition of the potion of power. */
	private static final float VANILLA_BITE_HP = 2.0f;

	/*One game day in ticks. 24000 = 20 minutes. */
	private static final int TICKS_PER_DAY = 24000;

	private PhantomThreats() {
	}

	/**
	 Damage that must be transferred to the impact so that the player loses exactly the number of settings.
	 The game itself reduces the damage of mobs on the lung and multiplies by 1.5 on the complex.
	 The potion of power remains on top and goes through the same count.
	 */
	public static float biteDamage(Phantom phantom, float incoming) {
		if (!ENABLED) {
			return incoming;
		}
		Difficulty difficulty = phantom.level().getDifficulty();
		float felt = biteFor(difficulty);
		float strengthBonus = Math.max(0.0f, incoming - VANILLA_BITE_HP);
		float raw = felt + strengthBonus;
		if (!(phantom.getTarget() instanceof Player)) {
			return raw;
		}
		return beforeDifficultyScale(difficulty, raw);
	}

	/**
	 How many phantoms to release in this wave?
	 * {@code vanillaCount} - how much a regular game chose (on a difficult 1 to 4).
	 On top of the insomnia increase, then the ceiling.
	 */
	public static int groupSize(ServerPlayer player, Difficulty difficulty, int vanillaCount) {
		if (!ENABLED) {
			return vanillaCount;
		}
		int ticksAwake = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));
		int daysAwake = ticksAwake / TICKS_PER_DAY;
		int extraDays = Math.max(0, daysAwake - CALM_DAYS);
		int step = Math.max(1, DAYS_PER_EXTRA_PHANTOM);
		int bonus = extraDays / step;
		int cap = switch (difficulty) {
			case EASY -> EASY_FLOCK_CAP;
			case NORMAL -> NORMAL_FLOCK_CAP;
			case HARD -> HARD_FLOCK_CAP;
			case PEACEFUL -> vanillaCount;
		};
		return Math.min(cap, vanillaCount + bonus);
	}

	private static float biteFor(Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> EASY_BITE_HP;
			case HARD -> HARD_BITE_HP;
			default -> NORMAL_BITE_HP;
		};
	}

	/**
	 * Reverse difficulty so that it remains {@code feltHp}
	 Easy: The game takes half the damage and adds 1, but not above the original.
	 Difficult: the game multiplies by 1.5. The normal number doesn't touch.
	 */
	private static float beforeDifficultyScale(Difficulty difficulty, float feltHp) {
		if (feltHp <= 0.0f) {
			return 0.0f;
		}
		return switch (difficulty) {
			case EASY -> feltHp <= 2.0f ? feltHp : (feltHp - 1.0f) * 2.0f;
			case HARD -> feltHp * 2.0f / 3.0f;
			default -> feltHp;
		};
	}
}
