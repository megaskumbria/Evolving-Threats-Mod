package com.example.entity.ai;

import com.example.zombie.ZombieSorcerer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 The magician's techniques. Walking and kicking a hoe does not cancel:
 The target is silent until it is a sorcerer.
 */
public class ZombieSorcererSpellGoal extends Goal {

	private final Zombie zombie;

	public ZombieSorcererSpellGoal(Zombie zombie) {
		this.zombie = zombie;
	}

	@Override
	public boolean canUse() {
		return ZombieSorcerer.isSorcerer(zombie);
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
		ZombieSorcerer.tick(zombie);
	}
}
