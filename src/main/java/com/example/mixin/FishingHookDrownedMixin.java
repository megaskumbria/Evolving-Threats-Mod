package com.example.mixin;

import com.example.drowned.DrownedFishing;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The usual hook lives only in the player and immediately disappears if the mob threw him.
 * A drowned man with a fishing rod has a flight and pull-up count {@link DrownedFishing}.
 */
@Mixin(FishingHook.class)
public class FishingHookDrownedMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$drownedRod(CallbackInfo ci) {
		FishingHook hook = (FishingHook) (Object) this;
		if (!(hook.getOwner() instanceof Drowned drowned)) {
			return;
		}
		if (hook.level().isClientSide()) {
			// The position has already smoothed the overall tick. Just keep the hook open.
			// The usual fishing rod lives only with the player.
			ci.cancel();
			return;
		}
		if (!DrownedFishing.tickBobber(hook, drowned)) {
			hook.discard();
		}
		ci.cancel();
	}
}
