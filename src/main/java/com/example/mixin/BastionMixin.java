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
 Ordinary zombies and an ordinary skeleton remember that they are Bastion.
 * The speed potion is not hung in {@link BastionSpeedMixin}.
 Husk and wanderer do not get this class: the field is Zombie and Skeleton.
 * But they don't get a chance - the husk and the wanderer have a different type.
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
