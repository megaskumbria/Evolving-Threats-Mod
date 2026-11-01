package com.example.client.mixin;

import com.example.drowned.DrownedFishing;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The line and hook are drawn only by the player's hand.
 * Here is the same line from the hand of the drowned man, if he threw the fishing rod.
 */
@Mixin(FishingHookRenderer.class)
public class DrownedFishingLineMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$showDrownedHook(
			FishingHook hook,
			net.minecraft.client.renderer.culling.Frustum culler,
			double camX,
			double camY,
			double camZ,
			float partialTicks,
			CallbackInfoReturnable<Boolean> cir) {
		if (hook.getOwner() instanceof Drowned drowned && DrownedFishing.holdsRod(drowned)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void evolvingthreats$drownedLine(FishingHook hook, FishingHookRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(hook.getOwner() instanceof Drowned drowned) || !DrownedFishing.holdsRod(drowned)) {
			return;
		}
		Vec3 hand = hand(drowned, partialTicks);
		Vec3 bobber = hook.getPosition(partialTicks).add(0.0, 0.25, 0.0);
		state.lineOriginOffset = hand.subtract(bobber);
	}

	/** Point in the right hand, like a player with a fishing rod, when the camera is not in the first person. */
	private static Vec3 hand(Drowned drowned, float partialTicks) {
		float body = Mth.lerp(partialTicks, drowned.yBodyRotO, drowned.yBodyRot) * (float) (Math.PI / 180.0);
		double sin = Mth.sin(body);
		double cos = Mth.cos(body);
		float scale = drowned.getScale();
		double side = 0.35 * scale;
		double forward = 0.8 * scale;
		return drowned.getEyePosition(partialTicks)
				.add(-cos * side - sin * forward, -0.45 * scale, -sin * side + cos * forward);
	}
}
