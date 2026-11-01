package com.example.digging;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;

import com.example.zombie.TntZombie;
import com.example.zombie.ZombieSorcerer;
import com.example.ritual.RitualBand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 Zombies break blocks: (1) to reach the player, (2) to destroy the light found.
 Here are all the settings and general rules (“can I break”, “how fast”).
 * Behavior itself: {@link com.example.entity.ai.BreakToTargetGoal}
 * and {@link com.example.entity.ai.LightSeekGoal} (lights).
 *
 The speed of breaking is considered as the player’s (block hardness and tool in the hand of zombies),
 * but multiplied by {@link #SPEED MULTIPLIER} so zombies aren't bulldozers.
 * The tool is suitable for the type: pick - stone, shovel - earth / sand, ax - wood.
 Material is important: an iron pick is faster than a wooden pick (like a player).
 */
public final class BlockBreaking {

	// ===================== SETTINGS (change these numbers) =====================

	/*Enable breaking blocks in general (both to the player and lights). False – zombie blocks don’t break. */
	public static final boolean ENABLED = true;

	/**
	 Proportion of zombies who can break blocks (0.0-1.0): 1.0 = all (test mode), 0.30 = 30%.
	 Solved once for each zombie (by its unique number), so the "breakers" do not change.
	 */
	public static final double BREAKER_FRACTION = 1.0;

	/**
	 Speed multiplier relative to player: 0.3 = 30% of player speed.
	 More is faster to break (1.0 = as a player). Less is slower.
	 Examples at 0.3: earth with a bare hand ~2.5 sec; stone with a bare hand ~25 sec.
	 Stone with an iron pickaxe ~1.5 seconds.
	 */
	public static final float SPEED_MULTIPLIER = 0.3f;

	/*Do items fall out of broken zombie blocks? False: The block just disappears. */
	public static final boolean DROP_ITEMS = false;

	/**
	 Blocks that zombies can never break.
	 Blocks with "non-breakable" hardness (barrier, command unit, etc.) are excluded automatically.
	 To ban something else, add Blocks. Name (e.g. Blocks.CRYING OBSIDIAN)
	 */
	public static final Set<Block> UNBREAKABLE_BLOCKS = Set.of(Blocks.BEDROCK, Blocks.OBSIDIAN);

	/*How far (blocks) from the target the zombie is still trying to break through to the player. He doesn't dig any further. */
	public static final double DIG_MAX_DISTANCE_TO_TARGET = 32.0;

	/*How often does a zombie check “I’m stuck?” when searching for a digging target (10 ticks = 0.5 seconds). */
	public static final int STUCK_CHECK_INTERVAL_TICKS = 10;

	/**
	 How many ticks does the crack on the block not reset if the zombie is distracted and returned (20 ticks = 1 sec).
	 100 = 5 seconds: hit, moved away, came back - breaks further from the same place.
	 0 = as before, after any pause, the block breaks again.
	 */
	public static final int KEEP_PROGRESS_TICKS = 100;

	/**
	 Dig down under your feet only if the player is at least as many blocks below the zombies.
	 3 = do not dig a hole on flat ground due to a small difference in height.
	 */
	public static final int DIG_DOWN_MIN_BLOCKS_BELOW = 3;

	/**
	 Dig down only if the player is not further than this horizontally (blocks).
	 Otherwise, the zombie digs down, although the player is on the side of the road.
	 */
	public static final double DIG_DOWN_MAX_HORIZONTAL = 8.0;

	/**
	 How many blocks above the feet of zombies (in the cage "in front of him" to the player) can be broken,
	 * to reach the player at the height. 2 = legs + 1 and legs + 2 (stand of 2 blocks).
	 */
	public static final int DIG_UP_MAX_BLOCKS_ABOVE_SELF = 2;

	/**
	 If the player is taller than the zombies, allow him to break the block right in front of his feet.
	 Otherwise, the zombie only hits the “head against the wall” and does not remove the stand under the player.
	 */
	public static final boolean DIG_UP_ALLOW_FRONT_AT_FEET = true;

	/**
	 Horizontal (blocks): closer to the player - do not dig if you can hit
	 * (see {@link #shouldPreferMeleeOverDig}). 2.0 = neighboring cell + diagonal.
	 */
	public static final double DIG_MELEE_HORIZONTAL_SNAP = 2.0;

	/**
	 The player is no higher than zombies by more than so many blocks - at close range, the priority of the strike is not digging.
	 */
	public static final double DIG_MELEE_VERTICAL_REACH = 3.0;

	/*Zombie arm range (blocks): You can’t break beyond this block. */
	public static final double REACH = 3.5;

	/**
	 Respect the game rule mobGriefing (team /gamerule mobGriefing false).
	 If the rule is turned off, zombie blocks do not break (like vanilla doors).
	 * False - they always break.
	 */
	public static final boolean RESPECT_MOB_GRIEFING = true;

	// ========================================================================

	private BlockBreaking() {
	}

	/*Can this zombie break blocks (a general rule for both behaviors) */
	public static boolean isBreaker(Zombie zombie) {
		if (!ENABLED) {
			return false;
		}
		// From what day do zombies break blocks (ThreatTable.BLOCK BREAK DAY)? The rate of destruction does not change from day to day.
		// The overseer and servants of the raid anchor always dig: they have their own appearance days.
		boolean ownSchedule = (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer())
				|| (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant());
		if (!ownSchedule && !ThreatScale.reached(zombie.level(), ThreatTable.BLOCK_BREAK_DAY)) {
			return false;
		}
		if (RESPECT_MOB_GRIEFING
				&& !(zombie.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		// Drowning is in the water, zombie piglins are not the upper world.
		// Zombies with dynamite do not dig the wall: if there is no way, it drops TNT.
		// The sorcerer does not dig, he has a hoe for the appearance, he is a sorcerer.
		// The ritual pack doesn't dig; otherwise it'll scatter away from its circle.
		if (zombie instanceof Drowned
				|| zombie instanceof ZombifiedPiglin
				|| TntZombie.isTntZombie(zombie)
				|| ZombieSorcerer.isSorcerer(zombie)
				|| RitualBand.isRitual(zombie)) {
			return false;
		}
		if (BREAKER_FRACTION >= 1.0) {
			return true;
		}
		// Stable UUID lot: The same zombie always breaks or doesn’t.
		long bits = zombie.getUUID().getLeastSignificantBits() ^ zombie.getUUID().getMostSignificantBits();
		double roll = ((bits >>> 1) % 10000) / 10000.0;
		return roll < BREAKER_FRACTION;
	}

	/*Is it possible to break this block (not air, not liquid, not hip/obsidian, not “eternal”)? */
	public static boolean canBreak(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
			return false;
		}
		if (UNBREAKABLE_BLOCKS.contains(state.getBlock())) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0f;
	}

	/**
	 How much “progress” (0.0–1.0 = block broken) a zombie gains in 1 tick on that block.
	 * Player-like formula: instrument speed/hardness/ (30 or 100) multiplied by SPEED MULTIPLIER.
	 100 instead of 30 - if you need a suitable tool for the block, but zombies do not have one (slower).
	 */
	/**
	 The player is close enough to hit – no digging is necessary (even if the pathfinder is stuck).
	 * Takes into account the vanilla radius and the crossing of the hitboxes (player on the next block on top).
	 */
	public static boolean shouldPreferMeleeOverDig(Zombie zombie, LivingEntity target) {
		if (zombie.isWithinMeleeAttackRange(target)) {
			return true;
		}
		double dx = target.getX() - zombie.getX();
		double dz = target.getZ() - zombie.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > DIG_MELEE_HORIZONTAL_SNAP) {
			return false;
		}
		double dy = target.getY() - zombie.getY();
		if (dy < -1.0 || dy > DIG_MELEE_VERTICAL_REACH) {
			return false;
		}
		AABB reach = zombie.getBoundingBox().inflate(0.85, 0.35, 0.85);
		return reach.intersects(target.getBoundingBox());
	}

	public static float progressPerTick(Zombie zombie, BlockGetter level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0f) {
			return 1.0f; // Instantly broken blocks (torch, grass)
		}
		ItemStack tool = zombie.getMainHandItem();
		float toolSpeed = tool.getDestroySpeed(state);
		boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float perTick = toolSpeed / hardness / (canHarvest ? 30.0f : 100.0f);
		return perTick * SPEED_MULTIPLIER;
	}
}
