package com.example.entity.ai;

import com.example.zombie.TntZombie;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 Zombies with dynamite check the path and health.
 He does not break blocks and does not cancel the impact: dynamite appears under the feet.
 */
public class TntDropGoal extends Goal {

	private final Zombie zombie;

	public TntDropGoal(Zombie zombie) {
		this.zombie = zombie;
	}

	@Override
	public boolean canUse() {
		return TntZombie.isTntZombie(zombie);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		TntZombie.tick(zombie);
	}
}
