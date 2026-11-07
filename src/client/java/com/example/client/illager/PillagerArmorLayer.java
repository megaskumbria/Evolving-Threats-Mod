package com.example.client.illager;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Glues player armor onto a pillager or vindicator.
 * The head is taller than a player's, and the torso is thicker. The numbers below are that fit.
 * The robe is hidden while armor is worn ({@link IllagerArmorFit}). The nose is left on the face. Restart to see a change.
 */
public class PillagerArmorLayer extends RenderLayer<IllagerRenderState, IllagerModel<IllagerRenderState>> {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Helmet shift up or down, in model pixels (16 = one block).
	 * A player helmet covers 8 pixels plus 1 of padding. The illager head is 10, so -1 sits the shell on the skull.
	 * -2 lifts it off the chin. 0 leaves the top of the head sticking out. Positive sinks it into the shoulders.
	 * Valid range about -4 to 4.
	 */
	public static final float HELMET_LIFT_Y = -1.0f;

	/**
	 * How far the chestplate sits below the neck, in model pixels (16 = one block). Positive is down.
	 * The illager mouth is the bottom two pixels of the head. 0 leaves the collar across the mouth.
	 * 2 clears the chin. Valid range 0–4. Above 4 the plate hangs off the hips.
	 */
	public static final float CHEST_DROP_Y = 2.0f;

	/**
	 * Chestplate and sleeve thickness, front to back. Both use this, so the shoulder seam stays closed.
	 * 1.0 = snug on the arm, flush with the torso (can flicker). 1.08 is a hair of padding.
	 * Valid range 1.0–1.5. Higher opens a gap at the shoulder and the sleeves look loose.
	 */
	public static final float CHEST_DEPTH_SCALE = 1.08f;

	/**
	 * Pants waist, front to back. Must stay under the chestplate and just outside the torso.
	 * The torso is 6 deep, the pants shell starts at 5, so 1.24 lands between them.
	 * Valid range 1.0–1.5. Higher than the chest scale and the pants stick out past the plate.
	 */
	public static final float LEGS_DEPTH_SCALE = 1.24f;

	/**
	 * true = a chestplate hides the folded robe-arms and shows the normal arms, so the sleeves have a limb to follow.
	 * Idle pillagers then stand with arms down instead of crossed. false = keep the crossed robe (it cuts through the plate).
	 */
	public static final boolean SHOW_REAL_ARMS_WITH_CHESTPLATE = true;

	// ========================================================================

	private final EquipmentLayerRenderer equipment;
	private final FittedArmorModel head;
	private final FittedArmorModel chest;
	private final FittedArmorModel legs;
	private final FittedArmorModel feet;

	public PillagerArmorLayer(
			net.minecraft.client.renderer.entity.RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>> renderer,
			ArmorModelSet<HumanoidModel<HumanoidRenderState>> models,
			EquipmentLayerRenderer equipment) {
		super(renderer);
		this.equipment = equipment;
		this.head = new FittedArmorModel(models.head(), EquipmentSlot.HEAD);
		this.chest = new FittedArmorModel(models.chest(), EquipmentSlot.CHEST);
		this.legs = new FittedArmorModel(models.legs(), EquipmentSlot.LEGS);
		this.feet = new FittedArmorModel(models.feet(), EquipmentSlot.FEET);
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			IllagerRenderState state,
			float yRot,
			float xRot) {
		if (!(state instanceof PillagerArmorState armorState)) {
			return;
		}
		capture(this.getParentModel(), armorState.evolvingthreats$getArmorPose());
		renderPiece(poseStack, submitNodeCollector, lightCoords, state, state.headEquipment, EquipmentSlot.HEAD, this.head);
		renderPiece(poseStack, submitNodeCollector, lightCoords, state, state.chestEquipment, EquipmentSlot.CHEST, this.chest);
		renderPiece(poseStack, submitNodeCollector, lightCoords, state, state.legsEquipment, EquipmentSlot.LEGS, this.legs);
		renderPiece(poseStack, submitNodeCollector, lightCoords, state, state.feetEquipment, EquipmentSlot.FEET, this.feet);
	}

	private static void capture(IllagerModel<?> model, PillagerArmorPose pose) {
		ModelPart root = model.root();
		pose.head.capture(root.getChild("head"));
		pose.body.capture(root.getChild("body"));
		pose.rightArm.capture(root.getChild("right_arm"));
		pose.leftArm.capture(root.getChild("left_arm"));
		pose.rightLeg.capture(root.getChild("right_leg"));
		pose.leftLeg.capture(root.getChild("left_leg"));
	}

	private void renderPiece(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			IllagerRenderState state,
			ItemStack stack,
			EquipmentSlot slot,
			FittedArmorModel model) {
		if (!HumanoidArmorLayer.shouldRender(stack, slot)) {
			return;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) {
			return;
		}
		EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS
				? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
				: EquipmentClientInfo.LayerType.HUMANOID;
		this.equipment.renderLayers(
				layerType,
				(ResourceKey<EquipmentAsset>) equippable.assetId().get(),
				model,
				state,
				stack,
				poseStack,
				submitNodeCollector,
				lightCoords,
				state.outlineColor);
	}
}
