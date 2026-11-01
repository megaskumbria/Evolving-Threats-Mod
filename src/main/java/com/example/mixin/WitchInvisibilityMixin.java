package com.example.mixin;

import com.example.witch.WitchInvisibility;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 A gulp of invisibility at the beginning of the witch's tick.
 The game then finishes the bottle and hangs the effect. When the sip comes, the throw doesn't start.
 */
@Mixin(Witch.class)
public class WitchInvisibilityMixin {

	/*How many tics do I have to keep the bottle at my mouth? The field of play is not written into the world. */
	@Shadow
	private int usingTime;

	/** Ticks until the next check. Less than zero, the chance has already fallen, waiting for a free sip. */
	@Unique
	private int evolvingthreats$invisibilityCheck;

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void evolvingthreats$drinkInvisibility(CallbackInfo ci) {
		WitchInvisibility.Drink drink = WitchInvisibility.tick((Witch) (Object) this, this.evolvingthreats$invisibilityCheck);
		this.evolvingthreats$invisibilityCheck = drink.cooldownTicks();
		if (drink.useTicks() > 0) {
			this.usingTime = drink.useTicks();
		}
	}
}
