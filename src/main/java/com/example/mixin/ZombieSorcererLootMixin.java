package com.example.mixin;

import com.example.zombie.ZombieSorcerer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sorcerer bonus drop. Loot is declared on Mob; Zombie has no separate override.
 */
@Mixin(net.minecraft.world.entity.Mob.class)
public class ZombieSorcererLootMixin {

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$sorcererBonus(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		if ((Object) this instanceof Zombie zombie) {
			ZombieSorcerer.dropBonus(zombie, level, killedByPlayer);
		}
	}
}
