package com.example.mixin;

import com.example.entity.MirrorEntity;
import com.example.mirror.Mirror;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Любой моб помнит флаг Mirror. Шанс бросают появления зомби, скелета и остальных из списка.
 * Запись в мире общая: свой finalizeSpawn есть не у всех видов из списка.
 */
@Mixin(Mob.class)
public class MirrorMixin implements MirrorEntity {

	@Unique
	private boolean evolvingthreats$mirror;

	@Override
	public boolean evolvingthreats$isMirror() {
		return evolvingthreats$mirror;
	}

	@Override
	public void evolvingthreats$setMirror(boolean mirror) {
		this.evolvingthreats$mirror = mirror;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveMirror(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$mirror) {
			output.putBoolean("EvolvingThreatsMirror", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadMirror(ValueInput input, CallbackInfo ci) {
		evolvingthreats$mirror = input.getBooleanOr("EvolvingThreatsMirror", false);
		if (evolvingthreats$mirror) {
			Mirror.refresh((Mob) (Object) this);
		}
	}
}
