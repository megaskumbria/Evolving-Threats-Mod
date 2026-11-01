package com.example;

import net.minecraft.world.entity.Mob;

/**
 * Named mobs do not always show their name tag.
 * Vanilla draws it when the crosshair is on the mob and it is within about 64 blocks.
 * Farther away the name stays hidden even if you aim precisely.
 * That limit is vanilla rules; this mod does not tune a separate distance.
 * Call after setting a custom name and again on world load,
 * or an existing mob may keep a name visible from far away.
 */
public final class MobNames {

	private MobNames() {
	}

	public static void showOnlyWhenLookedAt(Mob mob) {
		mob.setCustomNameVisible(false);
	}
}
