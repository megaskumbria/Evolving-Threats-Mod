package com.example.entity.ai;

import com.example.creeper.SapperCreeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 The bomber goes to the block under the feet of the player and explodes there. Explodes ALWAYS:
 * <ul>
 The path is there - runs up to the support (floor, top of the tower) and tears it up; >>
 * The tower without a way up - tears the base;</li >>
 There is no way (house, wall) - comes as close as possible, rests and after 8-10 seconds explodes against the wall,
 * Even if the player has gone deep into the house or is out of sight. </li/ >>
 * </ul>
 The player remembers {@link SapperCreeper#MEMORY SECONDS} seconds: vanilla AI quickly loses target without line of sight.
 */
public class SapperBreachGoal extends Goal {

	private final Creeper creeper;

	private UUID rememberedPlayerId;
	private long rememberedUntilGameTime;

	private int ticksUntilPathCheck;
	private boolean cantReach;

	private int ticksUntilStuckCheck;
	private Vec3 stuckCheckPos = Vec3.ZERO;
	private boolean stuck;

	private boolean wallTimerStarted;
	private long wallExplodeAt;

	public SapperBreachGoal(Creeper creeper) {
		this.creeper = creeper;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return SapperCreeper.isSapper(creeper) && resolveTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		ticksUntilPathCheck = 0;
		ticksUntilStuckCheck = SapperCreeper.STUCK_CHECK_TICKS;
		stuckCheckPos = creeper.position();
		stuck = false;
		cantReach = false;
		wallTimerStarted = false;
	}

	@Override
	public void stop() {
		wallTimerStarted = false;
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(-1);
		}
	}

	/*The target of the cryper, and if he lost sight of it - a remembered player (until the memory expires). */
	private LivingEntity resolveTarget() {
		long now = creeper.level().getGameTime();
		LivingEntity current = creeper.getTarget();
		if (current instanceof Player player && isValidPlayer(player)) {
			rememberedPlayerId = player.getUUID();
			rememberedUntilGameTime = now + SapperCreeper.MEMORY_SECONDS * 20L;
			return player;
		}
		if (current != null && current.isAlive()) {
			return current;
		}
		if (rememberedPlayerId != null && now < rememberedUntilGameTime) {
			Player remembered = creeper.level().getPlayerByUUID(rememberedPlayerId);
			if (remembered != null && isValidPlayer(remembered)
					&& creeper.distanceTo(remembered) <= SapperCreeper.CHASE_MAX_DISTANCE) {
				return remembered;
			}
		}
		return null;
	}

	private boolean isValidPlayer(Player player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& creeper.distanceTo(player) <= SapperCreeper.CHASE_MAX_DISTANCE;
	}

	@Override
	public void tick() {
		LivingEntity target = resolveTarget();
		if (target == null) {
			return;
		}
		creeper.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (creeper.isIgnited()) {
			creeper.getNavigation().stop();
			return;
		}

		long now = creeper.level().getGameTime();
		BlockPos support = SapperCreeper.supportUnder(target);
		double horizontal = SapperCreeper.horizontalDistance(creeper, target);
		boolean elevated = SapperCreeper.isElevated(creeper, target);
		boolean atSupport = creeper.distanceToSqr(support.getX() + 0.5, support.getY(), support.getZ() + 0.5)
				<= SapperCreeper.DETONATE_HORIZONTAL * SapperCreeper.DETONATE_HORIZONTAL;

		updateStuck();

		boolean recheckPath = ticksUntilPathCheck-- <= 0;
		if (recheckPath) {
			ticksUntilPathCheck = SapperCreeper.PATH_CHECK_TICKS;
			cantReach = !SapperCreeper.canReach(creeper, target) && !SapperCreeper.canReach(creeper, support);
		}

		// Tower or block underfoot. Hissing can be knocked down, moving away, like a regular cryper.
		// ignite() is not called here: after it the fuse is no longer extinguished, and the blow with a departure always ended in an explosion.
		boolean seesPlayer = SapperCreeper.canSee(creeper, target);
		if ((elevated && horizontal <= SapperCreeper.DETONATE_HORIZONTAL) || (!cantReach && atSupport)) {
			wallTimerStarted = false;
			creeper.getNavigation().stop();
			hiss(true);
			return;
		}

		if (!cantReach || seesPlayer) {
			// Clear field or player again in sight: catch up, not waiting against the wall.
			wallTimerStarted = false;
			hiss(false);
			if (recheckPath || creeper.getNavigation().isDone()) {
				creeper.getNavigation().moveTo(
						support.getX() + 0.5, support.getY(), support.getZ() + 0.5, SapperCreeper.MOVE_SPEED);
			}
			return;
		}

		// There is no way and the player is not visible (house, wall). Approach as we can, rest – then 8-10 seconds and explosion.
		// While we go, the countdown does not start: the kick and short stop in the field do not fall here.
		if (!wallTimerStarted && stuck) {
			wallTimerStarted = true;
			wallExplodeAt = now + SapperCreeper.randomWallDelayTicks(creeper);
		}

		if (wallTimerStarted) {
			creeper.getNavigation().stop();
			hiss(false);
			if (now >= wallExplodeAt) {
				SapperCreeper.detonate(creeper);
			}
			return;
		}

		hiss(false);
		if (recheckPath || creeper.getNavigation().isDone()) {
			creeper.getNavigation().moveTo(target, SapperCreeper.MOVE_SPEED);
		}
	}

	/*Hissing as long as the explosion condition holds. Moved away, deflated. We don't touch the burnt fuse. */
	private void hiss(boolean swelling) {
		if (!creeper.isIgnited()) {
			creeper.setSwellDir(swelling ? 1 : -1);
		}
	}

	/*Once a second checks if the cryper has moved. I didn't move, I hit the wall. */
	private void updateStuck() {
		if (ticksUntilStuckCheck-- > 0) {
			return;
		}
		ticksUntilStuckCheck = SapperCreeper.STUCK_CHECK_TICKS;
		Vec3 now = creeper.position();
		stuck = now.distanceTo(stuckCheckPos) < SapperCreeper.STUCK_MOVE_BLOCKS;
		stuckCheckPos = now;
	}
}
