package com.example.entity.ai;

import com.example.zombie.ZombieSorcerer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Приёмы чародея. Ходьбу и удар мотыгой не отменяет:
 * цель молчит, пока это не чародей.
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
