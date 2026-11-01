package com.example.entity.ai;

import com.example.spider.SpiderThreats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Spider-weaver: while he sees the player in the radius {@link SpiderThreats#WEB MAX DISTANCE},
 Every few seconds puts the web in the cage under the feet of the player.
 The spider's movement does not block - it continues to attack as usual.
 */
public class SpiderWebWeaveGoal extends Goal {

	private final Spider spider;
	private long nextWebGameTime;
	private boolean cooldownInitialized;

	public SpiderWebWeaveGoal(Spider spider) {
		this.spider = spider;
		this.setFlags(EnumSet.noneOf(Flag.class));
	}

	@Override
	public boolean canUse() {
		if (!SpiderThreats.isWeaver(spider) || !(spider.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!(spider.getTarget() instanceof Player target) || !target.isAlive()) {
			return false;
		}
		if (spider.distanceTo(target) > SpiderThreats.WEB_MAX_DISTANCE
				|| !spider.getSensing().hasLineOfSight(target)
				|| !SpiderThreats.webPlacementAllowed(level)) {
			return false;
		}
		long now = level.getGameTime();
		if (!cooldownInitialized) {
			// The first web is not immediately, but through a normal pause.
			nextWebGameTime = now + SpiderThreats.randomWebCooldownTicks(spider.getRandom());
			cooldownInitialized = true;
			return false;
		}
		return now >= nextWebGameTime;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		if (!(spider.level() instanceof ServerLevel level) || !(spider.getTarget() instanceof LivingEntity target)) {
			return;
		}
		boolean placed = SpiderThreats.placeWebUnderTarget(level, target);
		nextWebGameTime = level.getGameTime() + (placed
				? SpiderThreats.randomWebCooldownTicks(spider.getRandom())
				: SpiderThreats.WEB_RETRY_SECONDS * 20L);
	}
}
