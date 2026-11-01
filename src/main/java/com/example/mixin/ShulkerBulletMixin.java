package com.example.mixin;

import com.example.entity.ShulkerBulletTraits;
import com.example.shulker.ShulkerThreats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hit by a shalker. The game at this point hangs levitation for 10 seconds.
 If the impact did not pass, it does not come here: the shield and blinking do not give effects.
 */
@Mixin(ShulkerBullet.class)
public class ShulkerBulletMixin implements ShulkerBulletTraits {

	@Unique
	private boolean evolvingthreats$slowness;

	@Unique
	private boolean evolvingthreats$longLevitation;

	@Unique
	private boolean evolvingthreats$teleportsPlayer;

	@Redirect(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	private boolean evolvingthreats$shulkerHit(LivingEntity target, MobEffectInstance effect, Entity source) {
		return ShulkerThreats.onHit((ShulkerBullet) (Object) this, target, effect, source);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveBullet(ValueOutput output, CallbackInfo ci) {
		output.putBoolean("EvolvingThreatsShulkerBulletSlowness", evolvingthreats$slowness);
		output.putBoolean("EvolvingThreatsShulkerBulletLong", evolvingthreats$longLevitation);
		output.putBoolean("EvolvingThreatsShulkerBulletTeleport", evolvingthreats$teleportsPlayer);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadBullet(ValueInput input, CallbackInfo ci) {
		evolvingthreats$slowness = input.getBooleanOr("EvolvingThreatsShulkerBulletSlowness", false);
		evolvingthreats$longLevitation = input.getBooleanOr("EvolvingThreatsShulkerBulletLong", false);
		evolvingthreats$teleportsPlayer = input.getBooleanOr("EvolvingThreatsShulkerBulletTeleport", false);
	}

	@Override
	public boolean evolvingthreats$slowness() {
		return evolvingthreats$slowness;
	}

	@Override
	public void evolvingthreats$setSlowness(boolean slowness) {
		this.evolvingthreats$slowness = slowness;
	}

	@Override
	public boolean evolvingthreats$longLevitation() {
		return evolvingthreats$longLevitation;
	}

	@Override
	public void evolvingthreats$setLongLevitation(boolean longLevitation) {
		this.evolvingthreats$longLevitation = longLevitation;
	}

	@Override
	public boolean evolvingthreats$teleportsPlayer() {
		return evolvingthreats$teleportsPlayer;
	}

	@Override
	public void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer) {
		this.evolvingthreats$teleportsPlayer = teleportsPlayer;
	}
}
