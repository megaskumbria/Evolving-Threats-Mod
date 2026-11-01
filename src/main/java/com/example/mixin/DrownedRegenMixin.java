package com.example.mixin;

import com.example.drowned.DrownedThreats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Drowning treatment. Tick declared zombies, drowned does not.
 On land and in ordinary zombies nothing happens.
 */
@Mixin(Zombie.class)
public class DrownedRegenMixin {

	@Unique
	private int evolvingthreats$regenTicks = DrownedThreats.REGEN_SECONDS * 20;

	@Inject(method = "tick", at = @At("RETURN"))
	private void evolvingthreats$drownedRegen(CallbackInfo ci) {
		if (!((Object) this instanceof Drowned drowned) || !(drowned.level() instanceof ServerLevel)) {
			return;
		}
		evolvingthreats$regenTicks = DrownedThreats.tickRegen(drowned, evolvingthreats$regenTicks);
	}
}
