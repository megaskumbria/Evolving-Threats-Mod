package com.example.mixin;

import com.example.entity.SpiderRoleEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Запись/чтение роли паука в сохранении (метод объявлен в Mob, поэтому mixin на Mob). */
@Mixin(Mob.class)
public class SpiderRoleSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSpiderRole(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof SpiderRoleEntity data && data.evolvingthreats$getSpiderRole() != 0) {
			output.putInt("EvolvingThreatsSpiderRole", data.evolvingthreats$getSpiderRole());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSpiderRole(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof SpiderRoleEntity data) {
			data.evolvingthreats$setSpiderRole(input.getIntOr("EvolvingThreatsSpiderRole", 0));
		}
	}
}
