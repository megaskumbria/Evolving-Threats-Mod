package com.example.client.mixin;

import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pillager and vindicator renderers do not copy worn armor into the draw state (zombies do that themselves).
 * Without this, the armor layer has nothing to show.
 */
@Mixin(IllagerRenderer.class)
public class PillagerArmorExtractMixin {

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void evolvingthreats$copyWornArmor(
			AbstractIllager entity,
			IllagerRenderState state,
			float partialTicks,
			CallbackInfo ci) {
		if (!(entity instanceof Pillager) && !(entity instanceof Vindicator)) {
			return;
		}
		state.headEquipment = worn(entity, EquipmentSlot.HEAD);
		state.chestEquipment = worn(entity, EquipmentSlot.CHEST);
		state.legsEquipment = worn(entity, EquipmentSlot.LEGS);
		state.feetEquipment = worn(entity, EquipmentSlot.FEET);
	}

	private static ItemStack worn(LivingEntity entity, EquipmentSlot slot) {
		ItemStack stack = entity.getItemBySlot(slot);
		return HumanoidArmorLayer.shouldRender(stack, slot) ? stack.copy() : ItemStack.EMPTY;
	}
}
