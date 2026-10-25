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
 * Ночной спавн фантомов. Игра уже решила, что игроку пора стая, и бросает её размер.
 * Здесь к этому броску добавляются лишние фантомы за дни без кровати.
 * Яйцо призыва и команда спавна сюда не попадают: это только бессонница.
 */
@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

	/** Игрок, для которого прямо сейчас считается волна. Между игроками перезаписывается. */
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
	 * Бросок размера стаи. Игра потом прибавляет к нему 1, поэтому наружу уходит на 1 меньше.
	 * Срез начинается с номера сложности: раньше в этом методе другие случайные числа
	 * (пауза между попытками и место спавна), их не меняем.
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
		Difficulty difficulty = Difficulty.byId(bound - 1);
		int grown = PhantomThreats.groupSize(player, difficulty, vanillaCount);
		return grown - 1;
	}
}
