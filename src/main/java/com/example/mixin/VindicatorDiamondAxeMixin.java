package com.example.mixin;

import com.example.illager.VindicatorDiamondAxe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.illager.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Алмазный топор поборника.
 * Вне рейда топор выдаётся при появлении, и сразу после этого его можно заменить:
 * чары сложности и чары мода вешаются уже на алмаз.
 * В рейде игра потом выдаёт топор заново и сама его зачаровывает — замена ещё раз, чары остаются.
 */
@Mixin(Vindicator.class)
public class VindicatorDiamondAxeMixin {

	@Inject(method = "populateDefaultEquipmentSlots", at = @At("RETURN"))
	private void evolvingthreats$diamondAxeOnSpawn(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
		VindicatorDiamondAxe.tryReplace((Vindicator) (Object) this);
	}

	@Inject(method = "applyRaidBuffs", at = @At("RETURN"))
	private void evolvingthreats$diamondAxeOnRaid(ServerLevel level, int wave, boolean captain, CallbackInfo ci) {
		VindicatorDiamondAxe.tryReplace((Vindicator) (Object) this);
	}
}
