package com.example.mixin;

import com.example.entity.SpikyEntity;
import com.example.spiky.Spiky;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Обычный зомби и обычный скелет помнят, что они Spiky.
 * Хаск и странник этот класс тоже получают, потому что они наследники,
 * но шанс им не бросается: у кадавра и странника другой тип.
 */
@Mixin({ Zombie.class, Skeleton.class })
public class SpikyMixin implements SpikyEntity {

	@Unique
	private boolean evolvingthreats$spiky;

	@Override
	public boolean evolvingthreats$isSpiky() {
		return evolvingthreats$spiky;
	}

	@Override
	public void evolvingthreats$setSpiky(boolean spiky) {
		this.evolvingthreats$spiky = spiky;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSpiky(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$spiky) {
			output.putBoolean("EvolvingThreatsSpiky", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSpiky(ValueInput input, CallbackInfo ci) {
		evolvingthreats$spiky = input.getBooleanOr("EvolvingThreatsSpiky", false);
		if (evolvingthreats$spiky) {
			Spiky.refresh((Mob) (Object) this);
		}
	}
}
