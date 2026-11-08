package com.example.illager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 A champion cuts a tree if it stands between him and the goal.
 * Fence, gate, door, hatch, boards, logs, steps, plates, plates.
 Stone, Nezer fence and the ground does not touch.
 There is no chest and barrel either: otherwise things would disappear inside.
 *
 * If that goal hides in a wooden house, the game would normally forget it
 * (no line of sight). {@link #keepTargetThroughWood} puts the goal back
 * so the champion keeps chopping until he can see it again.
 */
public final class VindicatorWoodBreak {

	// ===================== SETTINGS (change these numbers) =====================

	/** False - the champion does not cut a tree. Doors in the raid the game breaks in its own way. */
	public static final boolean ENABLED = true;

	/**
	 Share of the speed of a player with the same axe. 0.2 = 20%.
	 The material of the axe is already within this speed: iron is faster than the hand, diamond is faster than iron.
	 Efficiency on the axe also accelerates, sharpness - no, it is only about the blow.
	 Oak fence (hardness 2): iron axe about 2.5 seconds, diamond about 1.9 seconds.
	 * 1.0 = as a player. 0 = the tree does not break.
	 */
	public static final float SPEED_MULTIPLIER = 0.2f;

	/** Whether the tree falls out. False: The block just disappears. */
	public static final boolean DROP_ITEMS = false;

	/** Beyond this (blocks) from the target fence is not cut. */
	public static final double MAX_DISTANCE_TO_TARGET = 32.0;

	/*How often do you check if the path has opened? 10 tics = half a second. */
	public static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 How many tics the crack does not shed if the champion is distracted and returned.
	 * 100 = 5 seconds.
	 */
	public static final int KEEP_PROGRESS_TICKS = 100;

	/** Arm range (blocks). Beyond that, the block doesn't cut. */
	public static final double REACH = 3.5;

	/**
	 * True - the command /gamerule mobGriefing false prohibits cutting.
	 * False - cut even when the rule is turned off.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	/**
	 * True — glass in the way is chopped like wood: blocks, panes, stained and tinted.
	 * The target behind that glass is not forgotten.
	 * False — glass stays, and a glass wall makes him lose the target as in the normal game.
	 */
	public static final boolean BREAK_GLASS = true;

	/**
	 * True — a target that stepped behind wood (planks, door, fence, logs) or glass is not forgotten.
	 * The champion walks up and chops until he can see the target again.
	 * False — hiding in a house makes him lose the target, as in the normal game.
	 */
	public static final boolean KEEP_TARGET_BEHIND_WOOD = true;

	/**
	 * After the champion has seen the target again, hiding behind stone or dirt
	 * drops the target after this many ticks. 60 = 3 seconds, same as the normal game.
	 * Does not apply while wood is still between them: that chase lasts until he sees the target,
	 * or the target is farther than the chase distance.
	 * 0 = once he can see the target again, the next hide that is not wood drops the target immediately.
	 */
	public static final int UNSEEN_FORGET_TICKS = 60;

	// ========================================================================

	/** Who the champion was chasing, and whether that chase is a wooden-cover chase. */
	private static final Map<Vindicator, Memory> MEMORY = new WeakHashMap<>();

	private static final class Memory {
		private UUID targetId;
		/** True while wood has hidden this target and the champion has not seen them since. */
		private boolean holding;
		/** True after a wooden-cover chase, so a later stone hide still expires like the normal game. */
		private boolean everHeld;
		/** Game time when the target was last hidden by something that is not wood. 0 = not counting. */
		private long lostSightAt;

		private Memory(UUID targetId) {
			this.targetId = targetId;
		}
	}

	private VindicatorWoodBreak() {
	}

	/**
	 * Call just before movement and chopping. If the game cleared the target because a wooden
	 * wall, door or fence is in the way, the target is put back. Stone and dirt do not count:
	 * after the usual few seconds the champion forgets, as before.
	 */
	public static void keepTargetThroughWood(Vindicator vindicator) {
		if (!KEEP_TARGET_BEHIND_WOOD || !canChop(vindicator) || !vindicator.isAlive()) {
			return;
		}
		LivingEntity current = vindicator.getTarget();
		Memory memory = MEMORY.get(vindicator);
		if (current != null && current.isAlive()) {
			if (memory == null || !memory.targetId.equals(current.getUUID())) {
				memory = new Memory(current.getUUID());
				MEMORY.put(vindicator, memory);
			}
		}
		if (memory == null) {
			return;
		}
		LivingEntity remembered = findRemembered(vindicator, memory.targetId);
		if (!isChaseable(vindicator, remembered)) {
			if (remembered != null && vindicator.getTarget() == remembered) {
				vindicator.setTarget(null);
			}
			MEMORY.remove(vindicator);
			return;
		}
		if (vindicator.getSensing().hasLineOfSight(remembered)) {
			memory.holding = false;
			memory.lostSightAt = 0L;
			return;
		}
		if (woodBlocksView(vindicator, remembered)) {
			memory.holding = true;
			memory.everHeld = true;
			memory.lostSightAt = 0L;
			if (vindicator.getTarget() != remembered) {
				vindicator.setTarget(remembered);
			}
			return;
		}
		if (memory.holding) {
			if (vindicator.getTarget() != remembered) {
				vindicator.setTarget(remembered);
			}
			return;
		}
		if (memory.everHeld && vindicator.getTarget() == remembered && forgetAfterUnseen(vindicator, memory)) {
			vindicator.setTarget(null);
			MEMORY.remove(vindicator);
			return;
		}
		if (vindicator.getTarget() == null) {
			MEMORY.remove(vindicator);
		}
	}

	/** True when the unseen timer has run out. A timer of 0 forgets immediately. */
	private static boolean forgetAfterUnseen(Vindicator vindicator, Memory memory) {
		if (UNSEEN_FORGET_TICKS <= 0) {
			return true;
		}
		long now = vindicator.level().getGameTime();
		if (memory.lostSightAt == 0L) {
			memory.lostSightAt = now;
			return false;
		}
		return now - memory.lostSightAt >= UNSEEN_FORGET_TICKS;
	}

	@Nullable
	private static LivingEntity findRemembered(Vindicator vindicator, UUID id) {
		if (!(vindicator.level() instanceof ServerLevel level)) {
			return null;
		}
		if (level.getEntity(id) instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/** Alive, same world, not a creative or spectator player, still inside the chase distance. */
	private static boolean isChaseable(Vindicator vindicator, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != vindicator.level()) {
			return false;
		}
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		double range = keepRange(vindicator);
		return vindicator.distanceToSqr(target) <= range * range;
	}

	/**
	 * How far (blocks) a wooden-cover chase still holds. At least {@link #MAX_DISTANCE_TO_TARGET},
	 * or the champion's own follow range if a raid made that longer.
	 */
	private static double keepRange(Vindicator vindicator) {
		var follow = vindicator.getAttribute(Attributes.FOLLOW_RANGE);
		double followRange = follow == null ? 0.0 : follow.getValue();
		return Math.max(MAX_DISTANCE_TO_TARGET, followRange);
	}

	/**
	 * Wood is what blocks the view: a ray to the eyes, the body or the feet hits planks,
	 * a door, a fence, logs, glass, and the other blocks from {@link #isChopBlock}. Stone does not.
	 */
	private static boolean woodBlocksView(Vindicator vindicator, LivingEntity target) {
		Vec3 from = vindicator.getEyePosition();
		Vec3 eyes = target.getEyePosition();
		Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 feet = new Vec3(target.getX(), target.getY() + 0.2, target.getZ());
		return rayHitsWood(vindicator, from, eyes)
				|| rayHitsWood(vindicator, from, body)
				|| rayHitsWood(vindicator, from, feet);
	}

	private static boolean rayHitsWood(Vindicator vindicator, Vec3 from, Vec3 to) {
		BlockHitResult hit = vindicator.level().clip(new ClipContext(
				from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vindicator));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		return isChopBlock(vindicator.level().getBlockState(hit.getBlockPos()));
	}

	public static boolean canChop(Vindicator vindicator) {
		if (!ENABLED || SPEED_MULTIPLIER <= 0.0f) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(vindicator.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		return true;
	}

	/*A tree or glass that prevents passing: there is a collision, and it is wood or glass. */
	public static boolean isWoodenObstacle(BlockGetter level, BlockPos pos, BlockState state) {
		if (!isChopBlock(state) || state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0f;
	}

	/** Wood from {@link #isWood}, plus glass when {@link #BREAK_GLASS} is on. */
	public static boolean isChopBlock(BlockState state) {
		return isWood(state) || isGlass(state);
	}

	/**
	 * Clear glass, tinted glass, stained glass, and the same as panes.
	 * Barrier is not glass: it is never broken.
	 */
	public static boolean isGlass(BlockState state) {
		if (!BREAK_GLASS) {
			return false;
		}
		var block = state.getBlock();
		return state.is(Blocks.GLASS)
				|| state.is(Blocks.GLASS_PANE)
				|| block instanceof TintedGlassBlock
				|| block instanceof StainedGlassBlock
				|| block instanceof StainedGlassPaneBlock;
	}

	/**
	 * Boards, logs, wooden fence and gate, door, hatch, steps, plates,
	 * button, pressure plate, shelf, sign, bamboo.
	 */
	public static boolean isWood(BlockState state) {
		return state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.LOGS)
				|| state.is(BlockTags.WOODEN_FENCES)
				|| state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.WOODEN_DOORS)
				|| state.is(BlockTags.WOODEN_TRAPDOORS)
				|| state.is(BlockTags.WOODEN_STAIRS)
				|| state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_BUTTONS)
				|| state.is(BlockTags.WOODEN_PRESSURE_PLATES)
				|| state.is(BlockTags.WOODEN_SHELVES)
				|| state.is(BlockTags.ALL_SIGNS)
				|| state.is(BlockTags.BAMBOO_BLOCKS);
	}

	/**
	 Progress in one tick, 1.0 = block broken.
	 * Formula like a player: axe speed / hardness / 30, then {@link #SPEED MULTIPLIER}
	 */
	public static float progressPerTick(Vindicator vindicator, BlockGetter level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0f) {
			return 1.0f;
		}
		ItemStack axe = vindicator.getMainHandItem();
		float toolSpeed = Math.max(1.0f, axe.getDestroySpeed(state));
		boolean canHarvest = !state.requiresCorrectToolForDrops() || axe.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * SPEED_MULTIPLIER;
	}
}
