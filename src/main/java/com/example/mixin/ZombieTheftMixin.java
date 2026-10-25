package com.example.mixin;

import com.example.zombie.ZombieTheft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Удар зомби. Срабатывает, когда удар уже снял здоровье.
 * Кто ворует и с каким шансом — {@link ZombieTheft}.
 */
@Mixin(Zombie.class)
public class ZombieTheftMixin {

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void evolvingthreats$steal(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		if (target instanceof Player player) {
			ZombieTheft.onHit((Zombie) (Object) this, player);
		}
	}
}
