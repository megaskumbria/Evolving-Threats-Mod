package com.example.entity.ai;

import com.example.illager.RavagerWallBreak;
import com.example.mixin.RavagerAttackAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 Hitting the head on the wall if there is no passage to the target.
 The soft blocks of the opening disappear immediately. The stone accumulates on the blows and falls when the blows were enough.
 */
public class RavagerHeadbuttGoal extends Goal {

	private final Ravager ravager;
	private final Map<BlockPos, Integer> hits = new HashMap<>();

	private long nextCheckGameTime;
	private long nextHeadbuttGameTime;
	private int animationTicks;
	private Vec3 lookAt = Vec3.ZERO;

	public RavagerHeadbuttGoal(Ravager ravager) {
		this.ravager = ravager;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!RavagerWallBreak.canBreak(ravager) || ravager.isPassenger()) {
			return false;
		}
		if (ravager.getStunnedTick() > 0 || ravager.getRoarTick() > 0) {
			return false;
		}
		long now = ravager.level().getGameTime();
		if (now < nextHeadbuttGameTime || now < nextCheckGameTime) {
			return false;
		}
		nextCheckGameTime = now + RavagerWallBreak.CHECK_INTERVAL_TICKS;
		return wallInFront() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return animationTicks > 0;
	}

	@Override
	public void start() {
		List<BlockPos> wall = wallInFront();
		if (wall == null) {
			animationTicks = 0;
			return;
		}
		lookAt = Vec3.atCenterOf(wall.getFirst());
		ravager.getLookControl().setLookAt(lookAt);
		ravager.getNavigation().stop();
		playHeadbutt();
		smash(wall);
		animationTicks = Ravager.ATTACK_DURATION;
		nextHeadbuttGameTime = ravager.level().getGameTime() + RavagerWallBreak.cooldownTicks(ravager);
	}

	@Override
	public void tick() {
		animationTicks--;
		ravager.getLookControl().setLookAt(lookAt);
		ravager.getNavigation().stop();
	}

	private void playHeadbutt() {
		((RavagerAttackAccessor) ravager).evolvingthreats$setAttackTick(Ravager.ATTACK_DURATION);
		if (ravager.level() instanceof ServerLevel server) {
			server.broadcastEntityEvent(ravager, (byte) 4);
		}
		ravager.playSound(SoundEvents.RAVAGER_ATTACK, 1.0f, 1.0f);
	}

	private void smash(List<BlockPos> wall) {
		Level level = ravager.level();
		hits.entrySet().removeIf(entry -> RavagerWallBreak.hitsNeeded(level, entry.getKey(), level.getBlockState(entry.getKey())) == 0);
		for (BlockPos pos : wall) {
			BlockState state = level.getBlockState(pos);
			int needed = RavagerWallBreak.hitsNeeded(level, pos, state);
			if (needed <= 0) {
				continue;
			}
			int done = hits.getOrDefault(pos, 0) + 1;
			if (done >= needed) {
				level.destroyBlockProgress(ravager.getId(), pos, -1);
				RavagerWallBreak.breakBlock(level, pos, ravager);
				hits.remove(pos);
			} else {
				hits.put(pos, done);
				int cracks = Math.min(9, done * 9 / needed);
				level.destroyBlockProgress(ravager.getId(), pos, cracks);
				level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 1.0f, 0.7f);
			}
		}
	}

	/*The wall in front of the muzzle, if the target is alive, it is far away for a bite and the way to it is not built. */
	private List<BlockPos> wallInFront() {
		LivingEntity target = ravager.getTarget();
		if (target == null || !target.isAlive()) {
			return null;
		}
		if (ravager.distanceTo(target) > RavagerWallBreak.MAX_DISTANCE_TO_TARGET) {
			return null;
		}
		if (hasReachablePath(target)) {
			return null;
		}
		Direction forward = horizontalDirectionTo(target);
		List<BlockPos> wall = RavagerWallBreak.nearestWall(ravager.level(), ravager.blockPosition(), forward);
		return wall.isEmpty() ? null : wall;
	}

	private boolean hasReachablePath(LivingEntity target) {
		Path path = ravager.getNavigation().createPath(target, 0);
		return path != null && path.canReach();
	}

	private Direction horizontalDirectionTo(LivingEntity target) {
		double dx = target.getX() - ravager.getX();
		double dz = target.getZ() - ravager.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0.0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
	}
}
