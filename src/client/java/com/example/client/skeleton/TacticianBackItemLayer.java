package com.example.client.skeleton;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Рисует лук или меч на спине. Модель скелета обычная, отдельная моделька не нужна.
 * Если предмет сидит криво — крути числа ниже и перезапусти игру.
 */
public class TacticianBackItemLayer<S extends SkeletonRenderState> extends RenderLayer<S, SkeletonModel<S>> {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** Сдвиг вправо, в блоках. 0 = по центру спины. Плюс — к правому плечу скелета. */
	private static final float OFFSET_X = 0.12F;

	/**
	 * Сдвиг вниз по спине, в блоках. 0 = у шеи, 0.38 = чуть выше середины,
	 * чтобы рукоять доставала до плеча. Больше — меч ниже, к поясу.
	 */
	private static final float OFFSET_Y = 0.38F;

	/**
	 * Насколько предмет отходит от спины, в блоках. 0.22 = лежит снаружи, не внутри рёбер.
	 * Если меч всё ещё в теле — увеличь. Если слишком далеко — уменьши.
	 */
	private static final float OFFSET_Z = 0.22F;

	/**
	 * Наклон на спине, в градусах.
	 * 0 = меч поперёк, горизонтально.
	 * -58 = рукоять к правому плечу, лезвие вниз.
	 * Плюс (например 58) — наоборот: лезвие вверх, рукоять вниз.
	 */
	private static final float ROT_Z = -58.0F;

	/** Поворот вокруг вертикали, в градусах. 0 = лицом наружу. 180 = развернуть картинку. */
	private static final float ROT_Y = 0.0F;

	/**
	 * Поворот «плашмя», в градусах.
	 * 0 = широкая сторона меча на спине. 90 = снова вбить ребром в тело.
	 */
	private static final float ROT_X = 0.0F;

	/**
	 * Размер. Картинка «плашмя» уже меньше, чем предмет в руке.
	 * 1.0 = обычный меч на спине. 0.7 = короче.
	 */
	private static final float SCALE = 1.0F;

	// ========================================================================

	public TacticianBackItemLayer(net.minecraft.client.renderer.entity.RenderLayerParent<S, SkeletonModel<S>> renderer) {
		super(renderer);
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			S state,
			float yRot,
			float xRot) {
		if (!(state instanceof TacticianBackItemState back)) {
			return;
		}
		ItemStackRenderState item = back.evolvingthreats$getBackItem();
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		poseStack.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
		poseStack.rotateDegrees(Axis.XP, ROT_X);
		poseStack.rotateDegrees(Axis.YP, ROT_Y);
		poseStack.rotateDegrees(Axis.ZP, ROT_Z);
		poseStack.scale(SCALE, SCALE, SCALE);
		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
