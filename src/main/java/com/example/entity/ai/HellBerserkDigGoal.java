package com.example.entity.ai;

import com.example.advancement.ModAdvancements;
import com.example.digging.BlockBreaking;
import com.example.piglin.HellBerserk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * While enraged, the berserk breaks the wall between him and the player.
 * Same idea as a zombie: only when there is no path, and he stops if he can already hit.
 * He does not dig before rage. Broken blocks do not drop items.
 */
public class HellBerserkDigGoal extends Goal {

	private final HellBerserk berserk;
	private long nextCheckGameTime;
	private BlockPos breakPos;
	private float progress;
	private int ticksSinceSwing;
	private boolean digging;

	public HellBerserkDigGoal(HellBerserk berserk) {
		this.berserk = berserk;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.berserk.isRaging() || this.berserk.isPassenger() || !griefingAllowed()) {
			return false;
		}
		long now = this.berserk.level().getGameTime();
		if (now < this.nextCheckGameTime) {
			return false;
		}
		this.nextCheckGameTime = now + BlockBreaking.STUCK_CHECK_INTERVAL_TICKS;
		return this.isBlockedFromTarget();
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.berserk.getTarget();
		return this.digging
				&& this.berserk.isRaging()
				&& target != null
				&& target.isAlive()
				&& griefingAllowed();
	}

	@Override
	public void start() {
		this.digging = true;
		this.ticksSinceSwing = 0;
	}

	@Override
	public void stop() {
		this.digging = false;
		this.clearCracks();
		this.breakPos = null;
		this.progress = 0.0f;
	}

	@Override
	public void tick() {
		LivingEntity target = this.berserk.getTarget();
		if (target == null) {
			return;
		}
		Level level = this.berserk.level();
		if (this.berserk.distanceTo(target) > BlockBreaking.DIG_MAX_DISTANCE_TO_TARGET
				|| this.preferMelee(target)
				|| this.hasReachablePath(target)) {
			this.stop();
			this.berserk.getNavigation().moveTo(target, 1.0);
			return;
		}
		if (this.breakPos != null) {
			BlockState state = level.getBlockState(this.breakPos);
			if (!this.isObstacle(level, this.breakPos, state)) {
				this.clearCracks();
				this.breakPos = null;
				this.progress = 0.0f;
			}
		}
		if (this.breakPos == null) {
			this.breakPos = this.pickBlock(target);
			this.progress = 0.0f;
			if (this.breakPos == null) {
				this.stop();
				this.berserk.getNavigation().moveTo(target, 1.0);
				return;
			}
		}
		this.mine(level);
	}

	private void mine(Level level) {
		BlockState state = level.getBlockState(this.breakPos);
		this.berserk.getLookControl().setLookAt(Vec3.atCenterOf(this.breakPos));
		this.berserk.getNavigation().stop();
		this.progress += progressPerTick(level, this.breakPos, state);
		this.ticksSinceSwing++;
		if (this.ticksSinceSwing >= 6) {
			this.ticksSinceSwing = 0;
			this.berserk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			level.playSound(null, this.breakPos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
		}
		if (this.progress >= 1.0f) {
			if (level.destroyBlock(this.breakPos, false, this.berserk)
					&& this.berserk.getTarget() instanceof ServerPlayer player) {
				ModAdvancements.onBlockBroken(player);
			}
			this.breakPos = null;
			this.progress = 0.0f;
		} else {
			level.destroyBlockProgress(this.berserk.getId(), this.breakPos, Math.min(9, (int) (this.progress * 10.0f)));
		}
	}

	private void clearCracks() {
		if (this.breakPos != null) {
			this.berserk.level().destroyBlockProgress(this.berserk.getId(), this.breakPos, -1);
		}
	}

	/** Same formula as a zombie, but the berserk's own speed and the axe in his hand. */
	private float progressPerTick(Level level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f) {
			return 0.0f;
		}
		if (hardness == 0.0f) {
			return 1.0f;
		}
		ItemStack tool = this.berserk.getMainHandItem();
		float toolSpeed = tool.getDestroySpeed(state);
		boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * HellBerserk.DIG_SPEED_MULTIPLIER;
	}

	private boolean griefingAllowed() {
		if (!BlockBreaking.RESPECT_MOB_GRIEFING) {
			return true;
		}
		return this.berserk.level() instanceof ServerLevel server
				&& server.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	private boolean isBlockedFromTarget() {
		LivingEntity target = this.berserk.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (this.berserk.distanceTo(target) > BlockBreaking.DIG_MAX_DISTANCE_TO_TARGET) {
			return false;
		}
		if (this.preferMelee(target) || this.hasReachablePath(target)) {
			return false;
		}
		return this.pickBlock(target) != null;
	}

	private boolean preferMelee(LivingEntity target) {
		if (this.berserk.isWithinMeleeAttackRange(target)) {
			return true;
		}
		double dx = target.getX() - this.berserk.getX();
		double dz = target.getZ() - this.berserk.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > BlockBreaking.DIG_MELEE_HORIZONTAL_SNAP) {
			return false;
		}
		double dy = target.getY() - this.berserk.getY();
		return dy >= -1.0 && dy <= BlockBreaking.DIG_MELEE_VERTICAL_REACH
				&& this.berserk.getBoundingBox().inflate(0.85, 0.35, 0.85).intersects(target.getBoundingBox());
	}

	private boolean hasReachablePath(LivingEntity target) {
		Path path = this.berserk.getNavigation().createPath(target, 0);
		return path != null && path.canReach();
	}

	private BlockPos pickBlock(LivingEntity target) {
		Level level = this.berserk.level();
		BlockPos feet = this.berserk.blockPosition();
		double dx = target.getX() - this.berserk.getX();
		double dz = target.getZ() - this.berserk.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY() - (feet.getY() + 0.5);
		if (dy <= -BlockBreaking.DIG_DOWN_MIN_BLOCKS_BELOW && horizontal <= BlockBreaking.DIG_DOWN_MAX_HORIZONTAL) {
			BlockPos below = feet.below();
			return this.isObstacle(level, below, level.getBlockState(below)) ? below : null;
		}
		Direction first = Math.abs(dx) >= Math.abs(dz)
				? (dx > 0 ? Direction.EAST : Direction.WEST)
				: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction second = Math.abs(dx) >= Math.abs(dz)
				? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
				: (dx > 0 ? Direction.EAST : Direction.WEST);
		int minY = target.getY() > feet.getY() + 0.25 ? feet.getY() : feet.getY() + 1;
		int maxY = Math.min(feet.getY() + BlockBreaking.DIG_UP_MAX_BLOCKS_ABOVE_SELF, target.blockPosition().getY());
		if (maxY < minY) {
			maxY = minY;
		}
		for (Direction dir : new Direction[] { first, second }) {
			BlockPos front = feet.relative(dir);
			for (int y = minY; y <= maxY; y++) {
				BlockPos candidate = new BlockPos(front.getX(), y, front.getZ());
				if (this.isObstacle(level, candidate, level.getBlockState(candidate))) {
					return candidate;
				}
			}
		}
		return null;
	}

	private boolean isObstacle(Level level, BlockPos pos, BlockState state) {
		if (state.getCollisionShape(level, pos).isEmpty() || !BlockBreaking.canBreak(level, pos, state)) {
			return false;
		}
		Block block = state.getBlock();
		// A portal is the way home. He smashes walls, not the door out.
		return block != Blocks.NETHER_PORTAL
				&& block != Blocks.END_PORTAL
				&& block != Blocks.END_PORTAL_FRAME
				&& block != Blocks.END_GATEWAY;
	}
}
