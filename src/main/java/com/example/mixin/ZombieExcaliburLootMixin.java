package com.example.mixin;

import com.example.zombie.ZombieExcalibur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Excalibur name tag. Loot hooks live on Mob; Zombie has no separate override.
 * The sword does not drop separately: hand drop chance is set to 0 when the sword is given.
 */
@Mixin(net.minecraft.world.entity.Mob.class)
public class ZombieExcaliburLootMixin {

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$excaliburTag(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		if ((Object) this instanceof Zombie zombie) {
			ZombieExcalibur.dropBonus(zombie, level);
		}
	}
}
