package com.example.mixin;

import com.example.entity.IllagerPatrolMember;
import com.example.entity.ai.IllagerPatrolMarchGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The memory of the detachment on the robber, champion and destroyer.
 The goal of “go to the point” is hung when creating a mob and is silent until the point is set.
 Preservation is necessary so that after unloading the chank they do not forget where they went.
 */
@Mixin(Raider.class)
public class RaiderPatrolMixin implements IllagerPatrolMember {

	@Unique
	@Nullable
	private BlockPos evolvingthreats$patrolDestination;

	@Override
	@Nullable
	public BlockPos evolvingthreats$getPatrolDestination() {
		return evolvingthreats$patrolDestination;
	}

	@Override
	public void evolvingthreats$setPatrolDestination(@Nullable BlockPos pos) {
		this.evolvingthreats$patrolDestination = pos;
	}

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$patrolMarch(CallbackInfo ci) {
		Raider self = (Raider) (Object) this;
		// Priority 5 is lower than hit and shot. As long as they see the target, they fight, not stomp at the point.
		self.getGoalSelector().addGoal(5, new IllagerPatrolMarchGoal(self, this));
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$savePatrol(ValueOutput output, CallbackInfo ci) {
		BlockPos pos = evolvingthreats$patrolDestination;
		if (pos == null) {
			return;
		}
		output.putBoolean("EvolvingThreatsPatrolMarch", true);
		output.putInt("EvolvingThreatsPatrolX", pos.getX());
		output.putInt("EvolvingThreatsPatrolY", pos.getY());
		output.putInt("EvolvingThreatsPatrolZ", pos.getZ());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadPatrol(ValueInput input, CallbackInfo ci) {
		if (!input.getBooleanOr("EvolvingThreatsPatrolMarch", false)) {
			return;
		}
		evolvingthreats$patrolDestination = new BlockPos(
				input.getIntOr("EvolvingThreatsPatrolX", 0),
				input.getIntOr("EvolvingThreatsPatrolY", 0),
				input.getIntOr("EvolvingThreatsPatrolZ", 0));
	}
}
