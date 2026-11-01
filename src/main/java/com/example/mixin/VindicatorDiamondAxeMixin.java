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
 Diamond axe champion.
 Outside the raid, the axe is issued when it appears, and immediately after that it can be replaced:
 The charms of complexity and fashion are hanging on the diamond.
 In the raid, the game then gives out the axe again and enchants it itself - the replacement again, the charms remain.
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
