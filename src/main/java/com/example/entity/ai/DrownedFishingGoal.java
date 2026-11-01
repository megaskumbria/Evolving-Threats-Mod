package com.example.entity.ai;

import com.example.drowned.DrownedFishing;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * The rod roll. Walking and kicking does not take: when the player is close, the usual attack of a drowned person hits.
 */
public class DrownedFishingGoal extends Goal {

	private final Drowned drowned;
	private FishingHook hook;
	private int cooldown;

	public DrownedFishingGoal(Drowned drowned) {
		this.drowned = drowned;
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		return DrownedFishing.wantsToCast(drowned);
	}

	@Override
	public boolean canContinueToUse() {
		if (!DrownedFishing.holdsRod(drowned)) {
			return false;
		}
		Player player = DrownedFishing.targetPlayer(drowned);
		if (player == null || drowned.distanceTo(player) <= DrownedFishing.MELEE_BLOCKS) {
			return false;
		}
		return hook != null && hook.isAlive();
	}

	@Override
	public void start() {
		Player player = DrownedFishing.targetPlayer(drowned);
		if (player == null) {
			return;
		}
		hook = DrownedFishing.cast(drowned, player);
	}

	@Override
	public void tick() {
		if (hook != null && !hook.isAlive()) {
			hook = null;
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		if (hook != null) {
			hook.discard();
			hook = null;
		}
		Player player = DrownedFishing.targetPlayer(drowned);
		boolean caught = player != null && drowned.distanceTo(player) <= DrownedFishing.MELEE_BLOCKS;
		cooldown = caught ? 0 : DrownedFishing.MISS_COOLDOWN_SECONDS * 20;
	}
}
