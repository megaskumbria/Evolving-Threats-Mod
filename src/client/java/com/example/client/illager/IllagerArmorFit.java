package com.example.client.illager;

import com.example.client.mixin.ModelPartCubeAccessor;
import com.example.client.mixin.ModelPartCubesAccessor;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The illager body is not a player's. While armor is worn, hide the robe that sticks through it.
 * The nose stays. A helmet has no hole for it, so the nose shows in front of the faceplate.
 * Runs at the end of the illager pose, right before that body is drawn.
 * Evokers and illusioners have no armor in the draw state, so they are left alone.
 */
public final class IllagerArmorFit {

	/**
	 * On the body part, cube 0 is the torso and cube 1 is the robe.
	 * The robe is taller than the chestplate and thicker than the pants, so it cuts through both.
	 * If a future game build adds cubes, only index 1 is hidden. A missing cube is skipped.
	 */
	private static final int ROBE_CUBE = 1;

	private static final ModelPart.Polygon[] HIDDEN = new ModelPart.Polygon[0];

	/** Original robe faces, so they can be put back when the chest and legs come off. */
	private static final Map<ModelPart.Cube, ModelPart.Polygon[]> ROBE_FACES = new IdentityHashMap<>();

	private IllagerArmorFit() {
	}

	public static void apply(IllagerModel<?> model, IllagerRenderState state) {
		boolean chest = HumanoidArmorLayer.shouldRender(state.chestEquipment, EquipmentSlot.CHEST);
		boolean legs = HumanoidArmorLayer.shouldRender(state.legsEquipment, EquipmentSlot.LEGS);
		boolean feet = HumanoidArmorLayer.shouldRender(state.feetEquipment, EquipmentSlot.FEET);

		ModelPart root = model.root();
		// The robe hangs to the boots, so any torso or leg piece needs it gone.
		setRobeHidden(root.getChild("body"), chest || legs || feet);

		if (chest && PillagerArmorLayer.SHOW_REAL_ARMS_WITH_CHESTPLATE && root.hasChild("arms")) {
			ModelPart folded = root.getChild("arms");
			if (folded.visible) {
				// The folded sleeves are one mesh in front of the chest. They cannot follow a chestplate.
				// The normal arms are already posed; showing them lets the armor sleeves sit on the limbs.
				folded.visible = false;
				root.getChild("right_arm").visible = true;
				root.getChild("left_arm").visible = true;
			}
		}
	}

	private static void setRobeHidden(ModelPart body, boolean hidden) {
		List<ModelPart.Cube> cubes = ((ModelPartCubesAccessor) (Object) body).evolvingthreats$cubes();
		if (cubes.size() <= ROBE_CUBE) {
			return;
		}
		ModelPart.Cube robe = cubes.get(ROBE_CUBE);
		ModelPartCubeAccessor access = (ModelPartCubeAccessor) (Object) robe;
		if (hidden) {
			ROBE_FACES.computeIfAbsent(robe, cube -> access.evolvingthreats$getPolygons());
			access.evolvingthreats$setPolygons(HIDDEN);
		} else {
			ModelPart.Polygon[] faces = ROBE_FACES.get(robe);
			if (faces != null) {
				access.evolvingthreats$setPolygons(faces);
			}
		}
	}
}
