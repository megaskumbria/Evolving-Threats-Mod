package com.example.mixin;

import com.example.zombie.ZombieMjolnir;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mjölnir name tag and rare hammer drop. Loot hooks live on Mob; Zombie has no separate override.
 * Vanilla never drops the hammer: hand drop chance is 0; {@link ZombieMjolnir} rolls the bonus.
 */
@Mixin(net.minecraft.world.entity.Mob.class)
public class ZombieMjolnirLootMixin {

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$mjolnirLoot(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		if ((Object) this instanceof Zombie zombie) {
			ZombieMjolnir.dropBonus(zombie, level);
		}
	}
}
