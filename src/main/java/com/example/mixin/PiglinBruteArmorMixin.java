package com.example.mixin;

import com.example.piglin.HellBerserk;
import com.example.piglin.PiglinBruteArmorHit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * End of a hit from any mob. Only a piglin brute or a Hell Berserk, and only a hit
 * that already took health off. Chance and armor damage — {@link PiglinBruteArmorHit}.
 */
@Mixin(Mob.class)
public class PiglinBruteArmorMixin {

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$shredArmor(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if (!(target instanceof Player player)) {
			return;
		}
		if ((Object) this instanceof PiglinBrute || (Object) this instanceof HellBerserk) {
			PiglinBruteArmorHit.onHit((LivingEntity) (Object) this, player);
		}
	}
}
