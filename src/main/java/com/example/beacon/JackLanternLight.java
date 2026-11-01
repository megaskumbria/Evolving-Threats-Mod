package com.example.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The light from the glowing pumpkin on the head. The object itself in the helmet slot does not illuminate the world,
 * therefore, an invisible block of light is placed next to the head and rides along with the mob.
 * The pumpkin is removed, the mob is dead or gone from the world — the lights go out.
 */
public final class JackLanternLight {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * How bright the pumpkin shines on the head. 0–15.
	 * 15 = as a block of Jack's lamp, the cave around the mob is illuminated.
	 * 7 = roughly like a smaller torch. 0 = no light.
	 */
	public static final int LIGHT_LEVEL = 15;

	// ========================================================================

	/** Where is the light of this mob now. After unloading the world, the map is empty: the light has already been removed by that time. */
	private static final Map<Mob, BlockPos> PLACED = new WeakHashMap<>();

	private static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE;

	private JackLanternLight() {
	}

	/** Every move of the mob. The light is rearranged only when the head has moved to another block. */
	public static void tick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (LIGHT_LEVEL <= 0 || !mob.isAlive() || !JackLanternBeacon.hasJackOnHead(mob)) {
			clear(mob);
			return;
		}
		BlockPos head = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
		BlockPos previous = PLACED.get(mob);
		if (head.equals(previous) && level.getBlockState(head).is(Blocks.LIGHT)) {
			return;
		}
		if (previous != null && !previous.equals(head)) {
			removeLight(level, previous);
		}
		BlockState at = level.getBlockState(head);
		if (at.isAir()) {
			level.setBlock(head, lightState(), UPDATE);
			PLACED.put(mob, head);
		} else if (at.is(Blocks.LIGHT)) {
			PLACED.put(mob, head);
		} else {
			PLACED.remove(mob);
		}
	}

	/** The mob has disappeared from the world: remove its light so that the eternal lamp does not remain in the cave. */
	public static void clear(Mob mob) {
		BlockPos previous = PLACED.remove(mob);
		if (previous != null && mob.level() instanceof ServerLevel level) {
			removeLight(level, previous);
		}
	}

	private static void removeLight(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE);
		}
	}

	private static BlockState lightState() {
		int level = Math.min(LightBlock.MAX_LEVEL, LIGHT_LEVEL);
		return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
	}
}
