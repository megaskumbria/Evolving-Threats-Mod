package com.example.mixin;

import com.example.bastion.Bastion;
import com.example.entity.BastionEntity;
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
 * Обычный зомби и обычный скелет помнят, что они Бастион.
 * Зелье скорости не вешается в {@link BastionSpeedMixin}.
 * Хаск и странник этот класс не получают: поле есть у Zombie и Skeleton,
 * но шанс им не бросается — у кадавра и странника другой тип.
 */
@Mixin({ Zombie.class, Skeleton.class })
public class BastionMixin implements BastionEntity {

	@Unique
	private boolean evolvingthreats$bastion;

	@Override
	public boolean evolvingthreats$isBastion() {
		return evolvingthreats$bastion;
	}

	@Override
	public void evolvingthreats$setBastion(boolean bastion) {
		this.evolvingthreats$bastion = bastion;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveBastion(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$bastion) {
			output.putBoolean("EvolvingThreatsBastion", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadBastion(ValueInput input, CallbackInfo ci) {
		evolvingthreats$bastion = input.getBooleanOr("EvolvingThreatsBastion", false);
		if (evolvingthreats$bastion) {
			Bastion.refresh((Mob) (Object) this);
		}
	}
}
