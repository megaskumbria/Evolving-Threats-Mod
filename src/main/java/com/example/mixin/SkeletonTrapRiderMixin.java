package com.example.mixin;

import com.example.skeleton.SkeletonHorseman;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.SkeletonTrapGoal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Грозовая ловушка после нашего набора заново чарует лук и шлем и, если голова пустая,
 * надевает железный шлем. Из-за этого щит и зелья остаются, а броня с оружием — нет.
 * Здесь этот второй проход выключен: наездник остаётся тем скелетом, которого одел мод.
 */
@Mixin(SkeletonTrapGoal.class)
public class SkeletonTrapRiderMixin {

	@Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$keepRiderEnchants(Skeleton skeleton, EquipmentSlot slot, DifficultyInstance difficulty, CallbackInfo ci) {
		if (SkeletonHorseman.ENABLED) {
			ci.cancel();
		}
	}

	@Redirect(
			method = "createSkeleton",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
	private boolean evolvingthreats$noForcedIronHelmet(ItemStack stack) {
		if (SkeletonHorseman.ENABLED) {
			return false;
		}
		return stack.isEmpty();
	}
}
