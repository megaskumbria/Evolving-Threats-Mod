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
 * Память отряда на разбойнике, поборнике и разорителе.
 * Цель «идти в точку» вешается при создании моба и молчит, пока точка не задана.
 * Сохранение нужно, чтобы после выгрузки чанка они не забыли, куда шли.
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
		// Приоритет 5 — ниже удара и выстрела. Пока видят цель, дерутся, а не топают в точку.
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
