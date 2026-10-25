package com.example.mixin;

import com.example.entity.TacticianSkeletonEntity;
import com.example.skeleton.TacticianSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Сохранение, смена лука и меча, выпадение предмета со спины. */
@Mixin(Mob.class)
public class TacticianSkeletonMobMixin {

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	private void evolvingthreats$tacticianTick(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof AbstractSkeleton skeleton) {
			TacticianSkeleton.serverTick(skeleton);
		}
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
	private void evolvingthreats$dropTacticianBackItem(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		if ((Object) this instanceof AbstractSkeleton skeleton) {
			TacticianSkeleton.dropBackItem(skeleton, level);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveTactician(ValueOutput output, CallbackInfo ci) {
		if (!((Object) this instanceof TacticianSkeletonEntity data) || !data.evolvingthreats$isTactician()) {
			return;
		}
		output.putBoolean("EvolvingThreatsTactician", true);
		ItemStack back = data.evolvingthreats$getBackItem();
		if (!back.isEmpty()) {
			output.store("EvolvingThreatsTacticianBack", ItemStack.CODEC, back);
		}
		if (data.evolvingthreats$getMarkedPlayer() != null) {
			output.putString("EvolvingThreatsTacticianTarget", data.evolvingthreats$getMarkedPlayer().toString());
		}
		if (data.evolvingthreats$getMeleeUntil() != 0L) {
			output.putLong("EvolvingThreatsTacticianMeleeUntil", data.evolvingthreats$getMeleeUntil());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadTactician(ValueInput input, CallbackInfo ci) {
		if (!((Object) this instanceof TacticianSkeletonEntity data)) {
			return;
		}
		if (!input.getBooleanOr("EvolvingThreatsTactician", false)) {
			return;
		}
		data.evolvingthreats$setTactician(true);
		input.read("EvolvingThreatsTacticianBack", ItemStack.CODEC).ifPresent(data::evolvingthreats$setBackItem);
		input.getString("EvolvingThreatsTacticianTarget").ifPresent(s -> data.evolvingthreats$setMarkedPlayer(UUID.fromString(s)));
		data.evolvingthreats$setMeleeUntil(input.getLongOr("EvolvingThreatsTacticianMeleeUntil", 0L));
	}
}
