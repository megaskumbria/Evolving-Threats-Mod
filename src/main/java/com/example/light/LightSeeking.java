package com.example.light;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.beacon.JackLanternBeacon;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 Zombies go to a bright light (torch, light stone, lantern...), if you do not see the player.
 * Search logic is here; go/stand/go further behavior is at {@link com.example.entity.ai.LightSeekGoal}.
 *
 Who participates: ordinary zombie, husk, zombie villager.
 Excluded: drowned (in water), zombie piglin (not the upper world),
 The guard and the servants of the raid (they have their own hunt).
 */
public final class LightSeeking {

	// ===================== SETTINGS (change these numbers) =====================

	/** Include behavior altogether. False: Zombies ignore light. */
	public static final boolean ENABLED = true;

	/**
	 Minimum brightness of the source (0-15) for zombies to notice.
	 * 12 = torch (14), light stone (15), lantern (15), fire (15), sea lamp (15).
	 Glowing lichen (7) and candles (up to 12 at 4 candles) - below / at the border.
	 Put 15 if you only need the brightest blocks.
	 */
	public static final int MIN_LIGHT_LEVEL = 12;

	/**
	 How far a zombie "seeks" light horizontally (blocks from its position).
	 60 = square 121×121; straight to ~60 blocks, diagonally to ~85.
	 The nearest suitable block wins in this volume.
	 * More radius = heavier for the server (many zombies without a target - don't inflate unnecessarily).
	 */
	public static final int SEARCH_RADIUS_HORIZONTAL = 60;

	/*Up and down from the feet of zombies (blocks): ± 16, a total of 33 floors in height. */
	public static final int SEARCH_RADIUS_VERTICAL = 16;

	/**
	 Require direct visibility to light (not through walls).
	 True - the light behind the wall zombies will not notice (of course, less false ways).
	 False - go to any light in radius, even behind the wall.
	 */
	public static final boolean REQUIRE_LINE_OF_SIGHT = true;

	/*A pause between searching for light when nothing is found (tics; 20 tics = 1 sec). */
	public static final int SCAN_INTERVAL_TICKS = 40;

	/*Speed of movement to light (1.0 = like a normal zombie step). */
	public static final double MOVE_SPEED = 1.0;

	/*Consider "come" when closer to this distance to light (blocks). */
	public static final double ARRIVE_DISTANCE = 2.5;

	/*How much to stand by the light (ticks): 80 = 4 seconds. */
	public static final int WAIT_AT_LIGHT_TICKS = 4 * 20;

	/**
	 Do zombies break the light they came to (if they can’t see the player)?
	 * Any light breaks except lava and light berries; blocks from UNBREAKABLE BLOCKS (hip, obsidian) do not.
	 It only works if a zombie can break blocks at all (see BlockBreaking.isBreaker).
	 */
	public static final boolean BREAK_LIGHT = true;

	// The breaking speed of light has no separate constant: it is the same as that of ordinary blocks.
	// (BlockBreaking.SPEED MULTIPLIER + block hardness + tool in zombie hand)
	// A torch/lamp with a hardness of 0 breaks immediately, a lightstone breaks in a couple of seconds.
	// If a zombie spots a player or a resident while it breaks, it will throw lights and hit its target.

	/*How much do not return to the light already visited (tics): 2400 = 2 minutes. */
	public static final int IGNORE_VISITED_LIGHT_TICKS = 2 * 60 * 20;

	/*Max. the time of one trip to the light (ticks) so as not to get stuck forever: 600 = 30 seconds. */
	public static final int MAX_WALK_TICKS = 30 * 20;

	/**
	 Residents are more important than light: if a zombie “feels” a resident nearby, he throws light
	 * (does not go to him and does not break) and goes to kill / infect a resident.
	 False - as before: a zombie without a goal first comes to light.
	 */
	public static final boolean VILLAGER_BEATS_LIGHT = true;

	/**
	 How far (blocks horizontally) the zombie feels. Through the walls too.
	 * 40 = just beyond the vanilla 35. Don't play SEARCH RADIUS HORIZONTAL anymore.
	 * More number = zombies "see" the village from afar and often throw light for it.
	 */
	public static final double VILLAGER_SENSE_RADIUS = 40.0;

	/**
	 How far up/down (blocks) the zombie feels.
	 In the vanilla only ± 4 - so the resident on the second floor or in the basement of the zombie "did not notice" and he went to break the torch.
	 */
	public static final double VILLAGER_SENSE_VERTICAL = 16.0;

	/*How often (ticks) zombies on the way to the light / light checks whether there was a resident nearby: 10 = half a second. */
	public static final int VILLAGER_RECHECK_TICKS = 10;

	// ========================================================================

	private LightSeeking() {
	}

	/*Could this zombie ever look for light? */
	public static boolean isEligible(Zombie zombie) {
		if (!ENABLED) {
			return false;
		}
		// From what day do zombies ever run into the light (ThreatTable.LIGHT SEEK DAY)?
		if (!ThreatScale.reached(zombie.level(), ThreatTable.LIGHT_SEEK_DAY)) {
			return false;
		}
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return false;
		}
		if (zombie instanceof ZombieOverseerEntity overseer && overseer.evolvingthreats$isOverseer()) {
			return false;
		}
		if (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return false;
		}
		// The lighthouse with the jack o'lantern itself shines - does not go to extinguish the torches.
		if (JackLanternBeacon.skipsLightSeeking(zombie)) {
			return false;
		}
		return true;
	}

	/**
	 If there is a live resident nearby (or an itinerant merchant, as in vanilla), make him a zombie target.
	 Returns true if the target is assigned (means the light is not interesting right now).
	 Next, the zombie himself goes to the resident with a regular attack (and the doors / blocks on the way break BreakToTargetGoal).
	 */
	public static boolean targetNearbyVillager(Zombie zombie) {
		if (!VILLAGER_BEATS_LIGHT) {
			return false;
		}
		AABB box = zombie.getBoundingBox().inflate(VILLAGER_SENSE_RADIUS, VILLAGER_SENSE_VERTICAL, VILLAGER_SENSE_RADIUS);
		AbstractVillager best = null;
		double bestDistSqr = Double.MAX_VALUE;
		for (AbstractVillager villager : zombie.level().getEntitiesOfClass(AbstractVillager.class, box, v -> v.isAlive())) {
			double distSqr = zombie.distanceToSqr(villager);
			if (distSqr < bestDistSqr) {
				best = villager;
				bestDistSqr = distSqr;
			}
		}
		if (best == null) {
			return false;
		}
		zombie.setTarget(best);
		return true;
	}

	/*Is the block a suitable light source? Lava, light berries (cave vines) - always not. */
	public static boolean isAttractiveLight(BlockState state) {
		if (state.getLightEmission() < MIN_LIGHT_LEVEL) {
			return false;
		}
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		if (state.is(Blocks.LAVA_CAULDRON)) {
			return false;
		}
		// Light berries on vines (brightness 14) are the natural light of caves, not the “player torch”.
		return !state.is(Blocks.CAVE_VINES) && !state.is(Blocks.CAVE_VINES_PLANT);
	}

	/*The nearest suitable light is around a zombie or null. {@code isIgnored} - positions already visited. */
	@Nullable
	public static BlockPos findNearestLight(Zombie zombie, java.util.function.Predicate<BlockPos> isIgnored) {
		Level level = zombie.level();
		BlockPos origin = zombie.blockPosition();
		int rh = SEARCH_RADIUS_HORIZONTAL;
		int rv = SEARCH_RADIUS_VERTICAL;

		BlockPos best = null;
		double bestDistSqr = Double.MAX_VALUE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -rh; dx <= rh; dx++) {
			for (int dz = -rh; dz <= rh; dz++) {
				cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
				// Do not touch unloaded chunks (otherwise the server will start loading them).
				if (!level.hasChunk(SectionPos.blockToSectionCoord(cursor.getX()), SectionPos.blockToSectionCoord(cursor.getZ()))) {
					continue;
				}
				for (int dy = -rv; dy <= rv; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!isAttractiveLight(state)) {
						continue;
					}
					double distSqr = zombie.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5);
					if (distSqr >= bestDistSqr) {
						continue;
					}
					BlockPos pos = cursor.immutable();
					if (isIgnored.test(pos)) {
						continue;
					}
					if (REQUIRE_LINE_OF_SIGHT && !canSee(zombie, pos)) {
						continue;
					}
					best = pos;
					bestDistSqr = distSqr;
				}
			}
		}
		return best;
	}

	/*Beam from the eyes of zombies to the center of the light block: not blocked by another block. */
	private static boolean canSee(Zombie zombie, BlockPos lightPos) {
		Vec3 from = zombie.getEyePosition();
		Vec3 to = Vec3.atCenterOf(lightPos);
		BlockHitResult hit = zombie.level().clip(new ClipContext(
				from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, zombie));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(lightPos);
	}
}
