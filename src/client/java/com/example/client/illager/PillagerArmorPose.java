package com.example.client.illager;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Where the pillager's head, body, arms, and legs were when this frame was posed.
 * Armor is drawn later, after the body model may already be posing the next pillager,
 * so the pose is copied here and applied then.
 */
public final class PillagerArmorPose {

	public final Part head = new Part();
	public final Part body = new Part();
	public final Part rightArm = new Part();
	public final Part leftArm = new Part();
	public final Part rightLeg = new Part();
	public final Part leftLeg = new Part();

	public static final class Part {
		public float x;
		public float y;
		public float z;
		public float xRot;
		public float yRot;
		public float zRot;
		public float xScale = 1.0f;
		public float yScale = 1.0f;
		public float zScale = 1.0f;
		public boolean visible = true;

		public void capture(ModelPart from) {
			this.x = from.x;
			this.y = from.y;
			this.z = from.z;
			this.xRot = from.xRot;
			this.yRot = from.yRot;
			this.zRot = from.zRot;
			this.xScale = from.xScale;
			this.yScale = from.yScale;
			this.zScale = from.zScale;
			this.visible = from.visible;
		}

		public void apply(ModelPart to) {
			to.x = this.x;
			to.y = this.y;
			to.z = this.z;
			to.xRot = this.xRot;
			to.yRot = this.yRot;
			to.zRot = this.zRot;
			to.xScale = this.xScale;
			to.yScale = this.yScale;
			to.zScale = this.zScale;
			to.visible = this.visible;
		}
	}
}
