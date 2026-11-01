package com.example.entity.ai;

import com.example.tool.FloatingTool;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 Flies to the player in the air and hits when the hitboxes touch.
 * Pause between beats - {@link FloatingTool#ATTACK COOLDOWN TICKS}
 Through the walls does not pass: the path is looking for an ordinary flying navigator.
 */
public class FloatingToolAttackGoal extends MeleeAttackGoal {

	private int nextAttackTick;

	public FloatingToolAttackGoal(PathfinderMob mob) {
		super(mob, FloatingTool.CHASE_SPEED, true);
	}

	@Override
	protected void checkAndPerformAttack(LivingEntity target) {
		if (this.mob.tickCount < this.nextAttackTick) {
			return;
		}
		if (!this.mob.isWithinMeleeAttackRange(target) || !this.mob.getSensing().hasLineOfSight(target)) {
			return;
		}
		this.nextAttackTick = this.mob.tickCount + FloatingTool.ATTACK_COOLDOWN_TICKS;
		this.mob.doHurtTarget(getServerLevel(this.mob), target);
	}
}
