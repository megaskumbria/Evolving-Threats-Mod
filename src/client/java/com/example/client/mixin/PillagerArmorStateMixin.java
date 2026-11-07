package com.example.client.mixin;

import com.example.client.illager.PillagerArmorPose;
import com.example.client.illager.PillagerArmorState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Every illager render state can remember a body pose. Only the pillager armor layer reads it. */
@Mixin(IllagerRenderState.class)
public class PillagerArmorStateMixin implements PillagerArmorState {

	@Unique
	private final PillagerArmorPose evolvingthreats$armorPose = new PillagerArmorPose();

	@Override
	public PillagerArmorPose evolvingthreats$getArmorPose() {
		return evolvingthreats$armorPose;
	}
}
 