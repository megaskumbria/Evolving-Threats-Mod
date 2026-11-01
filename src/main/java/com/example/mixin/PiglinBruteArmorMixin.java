package com.example.mixin;

import com.example.piglin.PiglinBruteArmorHit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 End of impact of any mob. Then we look only at the brutal piglin and only the punch.
 * who has already taken his health off. Chance and damage to the armor - {@link PiglinBruteArmorHit}.
 */
@Mixin(Mob.class)
public class PiglinBruteArmorMixin {

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$shredArmor(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if ((Object) this instanceof PiglinBrute brute && target instanceof Player player) {
			PiglinBruteArmorHit.onHit(brute, player);
		}
	}
}
