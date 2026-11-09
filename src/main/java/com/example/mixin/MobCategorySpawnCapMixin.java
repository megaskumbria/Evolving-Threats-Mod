package com.example.mixin;

import com.example.threat.HostileSpawnCap;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game asks "how many monsters may exist?" in two places: the world limit and the
 * per-player limit. Both read this number. Multiplying it lets the night fill dark ground
 * across the whole spawn area. Animals, the Nether, and the End are not changed.
 */
@Mixin(MobCategory.class)
public class MobCategorySpawnCapMixin {

	@Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$scaleMonsterCap(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this != MobCategory.MONSTER) {
			return;
		}
		float multiplier = HostileSpawnCap.multiplier();
		if (multiplier <= 1.0f) {
			return;
		}
		int base = cir.getReturnValue();
		if (base <= 0) {
			return;
		}
		int scaled = Math.round(base * multiplier);
		cir.setReturnValue(Math.max(base, scaled));
	}
}
