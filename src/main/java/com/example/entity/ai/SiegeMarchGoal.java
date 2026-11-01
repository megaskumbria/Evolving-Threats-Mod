package com.example.entity.ai;

import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 Leads the mob siege to the player in short stretches while he is far away.
 *
 Priority 1: Above fighting, wandering and running from the sun. While the mob is away, these behaviors
 * don't interrupt. Closer {@link SiegeWaves#MARCH STOP DISTANCE} target is off,
 * and the mob fights or shoots as usual.
 *
 While this target is led by a mob, the usual fight does not work, so she does not raise her hands.
 * and doesn't look at the player. Here the mob looks at the player and holds the battle stand all the way:
 The zombies have their hands up, the robber and champion have weapons in their hands, not a walk.
 Shoot and hit ahead of time it does not force - the blow is turned on only close.
 *
 The path set outside once per second, the game wipes with its wandering on the next tick.
 Therefore, you can only go to the player with a separate goal, as here.
 */
public class SiegeMarchGoal extends Goal {

	/**
	 How quickly the head turns to the player horizontally and vertically.
	 30 - as in a normal attack game. Less - the head catches up more slowly.
	 */
	private static final float LOOK_AT_PLAYER = 30.0f;

	private final Mob mob;
	private int nextRepathTick;
	private double lastX = Double.NaN;
	private double lastZ = Double.NaN;

	public SiegeMarchGoal(Mob mob) {
		this.mob = mob;
		// LOOK - so that a casual glance does not take your head away while the mob has to look at the player.
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.isPassenger() || !SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		// We're going beyond that distance again. Closer - already fighting or shooting, and we do not pull.
		return player != null && mob.distanceToSqr(player) > resumeSqr();
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.isPassenger() || !SiegeWaves.isSiegeMob(mob) || !SiegeNight.isWindowOpen(mob.level())) {
			return false;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		// The threshold is lower than that of canUse: otherwise, at the border, the target turns on and off every tick.
		// The mob twitches, and the skeleton can't fire.
		return player != null && mob.distanceToSqr(player) > stopSqr();
	}

	@Override
	public void start() {
		lastX = mob.getX();
		lastZ = mob.getZ();
		nextRepathTick = 0;
		holdCombatStance();
	}

	@Override
	public void stop() {
		// Otherwise, hands will remain raised when the siege is over or the mob is already nearby and fighting on its own.
		mob.setAggressive(false);
	}

	@Override
	public void tick() {
		// Every tick, not just when the path is rebuilt: otherwise the head looks toward walking.
		holdCombatStance();
		if (mob.tickCount < nextRepathTick) {
			return;
		}
		boolean done = mob.getNavigation().isDone();
		if (!done && !isStuck()) {
			return;
		}
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		if (player == null) {
			return;
		}
		// Even if the path is not built, the next attempt is not until after a pause.
		// Otherwise, every mob of siege looks for a way every tick, and the whole game goes in jerks.
		nextRepathTick = mob.tickCount + Math.max(1, SiegeWaves.REPATH_COOLDOWN_TICKS);
		lastX = mob.getX();
		lastZ = mob.getZ();

		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0E-4) {
			return;
		}
		if (!step(dx, dz, distance, SiegeWaves.MARCH_STEP_BLOCKS)) {
			step(dx, dz, distance, 8.0);
		}
	}

	/**
	 * Fight stand all the way. The usual fight at this time is turned off and the stand itself does not include.
	 * Closer {@link SiegeWaves#MARCH STOP DISTANCE} this target lets go of the mob and the fight goes on as usual.
	 */
	private void holdCombatStance() {
		ServerPlayer player = SiegeWaves.nearestHuntTarget(mob);
		if (player == null) {
			return;
		}
		mob.getLookControl().setLookAt(player, LOOK_AT_PLAYER, LOOK_AT_PLAYER);
		if (!mob.isAggressive()) {
			mob.setAggressive(true);
		}
	}

	/*It is almost in place, although the path is still listed. Then we build a new one. */
	private boolean isStuck() {
		if (Double.isNaN(lastX)) {
			return false;
		}
		double movedX = mob.getX() - lastX;
		double movedZ = mob.getZ() - lastZ;
		return movedX * movedX + movedZ * movedZ < 0.04;
	}

	/*The segment at the same height as the mob. A point on the surface of roofs and hills breaks the path. */
	private boolean step(double dx, double dz, double distance, double length) {
		double travel = Math.max(4.0, Math.min(length, distance - SiegeWaves.MARCH_STOP_DISTANCE));
		double x = mob.getX() + dx / distance * travel;
		double z = mob.getZ() + dz / distance * travel;
		return mob.getNavigation().moveTo(x, mob.getY(), z, SiegeWaves.MARCH_SPEED);
	}

	private static double stopSqr() {
		double stop = SiegeWaves.MARCH_STOP_DISTANCE;
		return stop * stop;
	}

	private static double resumeSqr() {
		double resume = Math.max(SiegeWaves.MARCH_STOP_DISTANCE + 4.0, SiegeWaves.MARCH_RESUME_DISTANCE);
		return resume * resume;
	}
}
