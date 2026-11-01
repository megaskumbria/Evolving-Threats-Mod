package com.example.entity.ai;

import com.example.grudge.PlayerGrudge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 Holds the target on the player from "memory" when vanilla dropped it (no line of sight).
 Priority 0 is above attack and going to the light. TARGET flag: does not interfere with movement, only the goal.
 */
public class ZombiePlayerGrudgeGoal extends Goal {

	private final Zombie zombie;

	public ZombiePlayerGrudgeGoal(Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		// First contact: vanilla just set the player on target - remember right away.
		LivingEntity current = zombie.getTarget();
		if (current instanceof Player player) {
			PlayerGrudge.rememberPlayer(zombie, player);
		}
		return PlayerGrudge.resolveGrudgePlayer(zombie) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		// The new vanilla target is the player: update the memory.
		LivingEntity current = zombie.getTarget();
		if (current instanceof Player player) {
			PlayerGrudge.rememberPlayer(zombie, player);
		}

		ServerPlayer grudge = PlayerGrudge.resolveGrudgePlayer(zombie);
		if (grudge == null) {
			return;
		}
		if (zombie.getTarget() != grudge) {
			zombie.setTarget(grudge);
		}
		// We extend the hunt if we see the player again (the vanilla itself holds the target).
		if (current == grudge) {
			PlayerGrudge.rememberPlayer(zombie, grudge);
		}
	}
}
