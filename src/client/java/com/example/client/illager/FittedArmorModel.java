package com.example.client.illager;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Player armor mesh, moved onto the pillager's bones each frame.
 * The game would otherwise pose it like a zombie, which does not match the crossbow arms.
 */
public final class FittedArmorModel extends Model<IllagerRenderState> {

	private final HumanoidModel<HumanoidRenderState> armor;
	private final EquipmentSlot slot;

	public FittedArmorModel(HumanoidModel<HumanoidRenderState> armor, EquipmentSlot slot) {
		super(armor.root(), armor.renderType());
		this.armor = armor;
		this.slot = slot;
	}

	@Override
	public void setupAnim(IllagerRenderState state) {
		this.resetPose();
		if (!(state instanceof PillagerArmorState armorState)) {
			return;
		}
		PillagerArmorPose pose = armorState.evolvingthreats$getArmorPose();
		switch (this.slot) {
			case HEAD -> {
				pose.head.apply(this.armor.head);
				this.armor.head.y += PillagerArmorLayer.HELMET_LIFT_Y;
			}
			case CHEST -> {
				pose.body.apply(this.armor.body);
				pose.rightArm.apply(this.armor.rightArm);
				pose.leftArm.apply(this.armor.leftArm);
				// Same thickness on the plate and the sleeves, or the shoulder shows a gap.
				this.armor.body.y += PillagerArmorLayer.CHEST_DROP_Y;
				this.armor.body.zScale *= PillagerArmorLayer.CHEST_DEPTH_SCALE;
				this.armor.rightArm.zScale *= PillagerArmorLayer.CHEST_DEPTH_SCALE;
				this.armor.leftArm.zScale *= PillagerArmorLayer.CHEST_DEPTH_SCALE;
			}
			case LEGS -> {
				pose.body.apply(this.armor.body);
				pose.rightLeg.apply(this.armor.rightLeg);
				pose.leftLeg.apply(this.armor.leftLeg);
				this.armor.body.zScale *= PillagerArmorLayer.LEGS_DEPTH_SCALE;
			}
			case FEET -> {
				pose.rightLeg.apply(this.armor.rightLeg);
				pose.leftLeg.apply(this.armor.leftLeg);
			}
			default -> {
			}
		}
	}
}
