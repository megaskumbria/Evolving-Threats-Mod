package com.example.mixin;

import com.example.siege.SiegeNight;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The game asks the entity “can you now go through the portal” while it is in the portal.
 During a siege, the player in the upper world says no: the portal to Hell or End does not work.
 * Decides {@link SiegeNight#blocksPortal}: Upper world players only, and only while the siege is on.
 Mobs and items, as well as players in Hell and End are not affected.
 */
@Mixin(Entity.class)
public class EntityPortalSiegeMixin {

	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$siegeBlocksPortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		if (SiegeNight.blocksPortal((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
