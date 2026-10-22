package com.example.mixin;

import com.example.entity.SpawnerHeadCarrierEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class MobSpawnerHeadSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSpawnerHead(ValueOutput output, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (!(self instanceof SpawnerHeadCarrierEntity carrier) || !carrier.evolvingthreats$isSpawnerHeadCarrier()) {
			return;
		}
		output.putBoolean("EvolvingThreatsSpawnerHead", true);
		output.putInt("EvolvingThreatsSpawnerHeadHits", carrier.evolvingthreats$getSpawnerHeadHitCount());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSpawnerHead(ValueInput input, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (!(self instanceof SpawnerHeadCarrierEntity carrier)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsSpawnerHead", false)) {
			return;
		}
		carrier.evolvingthreats$setSpawnerHeadCarrier(true);
		carrier.evolvingthreats$setSpawnerHeadHitCount(input.getIntOr("EvolvingThreatsSpawnerHeadHits", 0));
		// Старые миры: EvolvingThreatsSpawnerHeadSummoned больше не используется — призыв снова каждые 2 удара.
	}
}
