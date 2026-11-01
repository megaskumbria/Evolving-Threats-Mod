package com.example.entity.ai;

import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Keeps the player a target at the siege mob.
 *
 TargetSelector: Priority 0 is higher than the closest player of the game.
 Otherwise, the game every tick forgets the player: it is not visible from 50-70 blocks, and the mob stands still.
 As long as the target is held here, the usual fight and shooting themselves kick in when the mob comes close.
 *
 It does not work on ordinary mobs: it works only with a siege mark and only while the siege is underway.
 */
public class SiegeTargetGoal extends Goal {

	private final Mob mob;

	public SiegeTargetGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (!SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		return SiegeWaves.nearestHuntTarget(mob) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		if (player != null && mob.getTarget() != player) {
			mob.setTarget(player);
		}
	}

	@Override
	public void stop() {
		if (mob.getTarget() instanceof ServerPlayer) {
			mob.setTarget(null);
		}
	}
}
