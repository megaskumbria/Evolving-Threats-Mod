package com.example.whiteeyes;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/** Wires the visit night into the world tick and the bed. */
public final class WhiteEyesEvents {

	private WhiteEyesEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(WhiteEyesNight::tick);
		EntitySleepEvents.ALLOW_SLEEPING.register(WhiteEyesNight::onAllowSleeping);
		// A punched flame stays out. The swing is recorded as well as the break:
		// the break event sometimes never arrives for fire, and the patch would put it back.
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world instanceof ServerLevel level && level.getBlockState(pos).is(Blocks.FIRE)) {
				WhiteEyes.onFirePunched(level, pos);
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && state.is(Blocks.FIRE)) {
				WhiteEyes.onFirePunched(level, pos);
			}
		});
	}
}
