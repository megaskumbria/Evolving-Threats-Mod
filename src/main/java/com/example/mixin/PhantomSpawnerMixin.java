package com.example.mixin;

import com.example.phantom.PhantomThreats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 The Phantom Night Spawn. The game has already decided that it is time for the player to pack, and throws its size.
 Here, extra phantoms for days without a bed are added to this throw.
 The draft egg and the spawn team do not fall here: it is only insomnia.
 */
@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

	/*A player for whom the wave is considered right now. It's re-recording between players. */
	@Unique
	private ServerPlayer evolvingthreats$sleepless;

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;getStats()Lnet/minecraft/stats/ServerStatsCounter;"
			)
	)
	private ServerStatsCounter evolvingthreats$rememberSleepless(ServerPlayer player) {
		this.evolvingthreats$sleepless = player;
		return player.getStats();
	}

	/**
	 * Pack size throw. The game then adds 1 to it, so it goes out 1 less.
	 Slice starts with difficulty number: earlier in this method other random numbers
	 * (pause between attempts and place of spawn), they do not change.
	 */
	@Redirect(
			method = "tick",
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/world/Difficulty;getId()I"
					)
			),
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"
			)
	)
	private int evolvingthreats$growFlock(RandomSource random, int bound) {
		int vanillaRoll = random.nextInt(bound);
		ServerPlayer player = this.evolvingthreats$sleepless;
		if (player == null) {
			return vanillaRoll;
		}
		int vanillaCount = 1 + vanillaRoll;
		Difficulty difficulty = difficultyById(bound - 1);
		int grown = PhantomThreats.groupSize(player, difficulty, vanillaCount);
		return grown - 1;
	}

	/*Difficulty numbers: 0 peaceful, 1 easy, 2 normal, 3 difficult. */
	private static Difficulty difficultyById(int id) {
		for (Difficulty difficulty : Difficulty.values()) {
			if (difficulty.getId() == id) {
				return difficulty;
			}
		}
		return Difficulty.NORMAL;
	}
}
