package com.example.entity.ai;

import com.example.digging.BlockBreaking;
import com.example.grudge.PlayerGrudge;
import com.example.light.LightSeeking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 A zombie without a target goes to the nearest bright light, stands there and goes on.
 As soon as the target (player) appears, the vanilla attack (priority above) interrupts that target.
 Residents are more important than light: if there is a resident nearby, a zombie takes him to the target (see LightSeeking.VILLAGER BEATS LIGHT).
 */
public class LightSeekGoal extends Goal {

	private final Zombie zombie;

	/*Visited positions of light → game time until which they are ignored. */
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

	private BlockPos lightPos;
	private long nextScanGameTime;
	private int walkTicks;
	private int waitTicks;
	/*How much light has already been broken (0.0–1.0, 1.0 = block destroyed). */
	private float breakProgress;
	private boolean arrived;
	private boolean finished;

	public LightSeekGoal(Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!LightSeeking.isEligible(zombie) || zombie.isPassenger()) {
			return false;
		}
		// A player in goal or memory is not interested in light.
		if (zombie.getTarget() != null || PlayerGrudge.isPursuingPlayer(zombie)) {
			return false;
		}
		long now = zombie.level().getGameTime();
		if (now < nextScanGameTime) {
			return false;
		}
		// A resident is more important than light: we make it a target and do not touch the light.
		if (LightSeeking.targetNearbyVillager(zombie)) {
			return false;
		}
		// Random shift so that all zombies are not scanned in the same tick.
		nextScanGameTime = now + LightSeeking.SCAN_INTERVAL_TICKS + zombie.getRandom().nextInt(20);

		cleanupIgnored(now);
		BlockPos found = LightSeeking.findNearestLight(zombie, pos -> isIgnored(pos, now));
		if (found == null) {
			return false;
		}
		lightPos = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return !finished && zombie.getTarget() == null && !PlayerGrudge.isPursuingPlayer(zombie)
				&& LightSeeking.isEligible(zombie);
	}

	@Override
	public void start() {
		walkTicks = 0;
		waitTicks = 0;
		breakProgress = 0.0f;
		arrived = false;
		finished = false;
		// reachRange 2: It is enough to come close - the light can hang in the wall or on the ceiling.
		boolean ok = zombie.getNavigation().moveTo(
				lightPos.getX() + 0.5, lightPos.getY(), lightPos.getZ() + 0.5, 2, LightSeeking.MOVE_SPEED);
		if (!ok) {
			// There is no way – remember, so as not to try again at once.
			markVisited();
			finished = true;
		}
	}

	@Override
	public void tick() {
		if (finished || lightPos == null) {
			return;
		}
		// While it goes to the light or breaks it, we periodically check whether a resident has appeared.
		// Found → target assigned, target “light” is completed (canContinueToUse will see target!= null), cracks are removed in stop().
		if (zombie.tickCount % LightSeeking.VILLAGER_RECHECK_TICKS == 0 && LightSeeking.targetNearbyVillager(zombie)) {
			finished = true;
			return;
		}
		// The light was removed (broken torch) - there is nowhere else to go.
		if (!LightSeeking.isAttractiveLight(zombie.level().getBlockState(lightPos))) {
			markVisited();
			finished = true;
			return;
		}

		double distSqr = zombie.distanceToSqr(lightPos.getX() + 0.5, lightPos.getY() + 0.5, lightPos.getZ() + 0.5);
		double arrive = LightSeeking.ARRIVE_DISTANCE;
		// “I came”: came close, or the path ended, but you can reach the light with your hand (light on the ceiling).
		double eyeDistSqr = zombie.getEyePosition().distanceToSqr(lightPos.getX() + 0.5, lightPos.getY() + 0.5, lightPos.getZ() + 0.5);
		boolean inReach = eyeDistSqr <= BlockBreaking.REACH * BlockBreaking.REACH;
		if (!arrived && (distSqr <= arrive * arrive || (zombie.getNavigation().isDone() && inReach))) {
			arrived = true;
			zombie.getNavigation().stop();
		}

		if (arrived) {
			waitTicks++;
			BlockState lightState = zombie.level().getBlockState(lightPos);
			boolean willBreak = LightSeeking.BREAK_LIGHT && inReach
					&& BlockBreaking.isBreaker(zombie)
					&& BlockBreaking.canBreak(zombie.level(), lightPos, lightState);

			if (willBreak) {
				// We break the same rules as regular blocks (BlockBreaking.progressPerTick):
				// It depends on the hardness of the block and the tool in the hand of the zombies. Cracks are growing on the block.
				zombie.getLookControl().setLookAt(Vec3.atCenterOf(lightPos));
				breakProgress += BlockBreaking.progressPerTick(zombie, zombie.level(), lightPos, lightState);
				if (breakProgress >= 1.0f) {
					clearCracks();
					zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
					zombie.level().destroyBlock(lightPos, BlockBreaking.DROP_ITEMS, zombie);
					markVisited();
					finished = true;
				} else {
					if (waitTicks % 6 == 0) {
						zombie.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
					}
					if (waitTicks % 5 == 0) {
						int stage = Math.min(9, (int) (breakProgress * 10.0f));
						zombie.level().destroyBlockProgress(zombie.getId(), lightPos, stage);
					}
				}
			} else if (waitTicks >= LightSeeking.WAIT_AT_LIGHT_TICKS) {
				markVisited();
				finished = true;
			}
			return;
		}

		walkTicks++;
		if (walkTicks >= LightSeeking.MAX_WALK_TICKS || zombie.getNavigation().isDone()) {
			// Not reached (stuck / the path is over) - consider the visit, go on.
			markVisited();
			finished = true;
		}
	}

	@Override
	public void stop() {
		zombie.getNavigation().stop();
		clearCracks();
		lightPos = null;
		finished = false;
	}

	/*Remove cracks from the light block (zombie dropped it or has already broken it). */
	private void clearCracks() {
		if (lightPos != null) {
			zombie.level().destroyBlockProgress(zombie.getId(), lightPos, -1);
		}
	}

	private void markVisited() {
		if (lightPos != null) {
			ignoredUntil.put(lightPos, zombie.level().getGameTime() + LightSeeking.IGNORE_VISITED_LIGHT_TICKS);
		}
	}

	private boolean isIgnored(BlockPos pos, long now) {
		Long until = ignoredUntil.get(pos);
		return until != null && until > now;
	}

	private void cleanupIgnored(long now) {
		if (ignoredUntil.size() > 32) {
			ignoredUntil.values().removeIf(until -> until <= now);
		}
	}
}
