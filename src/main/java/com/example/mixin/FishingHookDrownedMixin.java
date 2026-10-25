package com.example.mixin;

import com.example.drowned.DrownedFishing;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Обычный крючок живёт только у игрока и сразу пропадает, если его бросил моб.
 * У утопленника с удочкой полёт и подтягивание считает {@link DrownedFishing}.
 */
@Mixin(FishingHook.class)
public class FishingHookDrownedMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$drownedRod(CallbackInfo ci) {
		FishingHook hook = (FishingHook) (Object) this;
		if (!(hook.getOwner() instanceof Drowned drowned)) {
			return;
		}
		if (!DrownedFishing.tickBobber(hook, drowned)) {
			hook.discard();
		}
		ci.cancel();
	}
}
