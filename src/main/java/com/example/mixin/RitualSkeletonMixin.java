package com.example.mixin;

import com.example.entity.RitualMember;
import com.example.ritual.RitualBand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Скелет помнит, что он из ритуальной стаи. Запись в мир объявлена у Skeleton. */
@Mixin(Skeleton.class)
public class RitualSkeletonMixin implements RitualMember {

	@Unique
	private boolean evolvingthreats$ritual;

	@Unique
	private int evolvingthreats$ritualHead;

	@Unique
	private UUID evolvingthreats$ritualGroup;

	@Unique
	private boolean evolvingthreats$ritualTorch;

	@Unique
	private boolean evolvingthreats$ritualExploded;

	@Override
	public boolean evolvingthreats$isRitual() {
		return evolvingthreats$ritual;
	}

	@Override
	public void evolvingthreats$setRitual(boolean ritual) {
		this.evolvingthreats$ritual = ritual;
	}

	@Override
	public int evolvingthreats$getRitualHead() {
		return evolvingthreats$ritualHead;
	}

	@Override
	public void evolvingthreats$setRitualHead(int head) {
		this.evolvingthreats$ritualHead = head;
	}

	@Override
	public UUID evolvingthreats$getRitualGroup() {
		return evolvingthreats$ritualGroup;
	}

	@Override
	public void evolvingthreats$setRitualGroup(UUID group) {
		this.evolvingthreats$ritualGroup = group;
	}

	@Override
	public boolean evolvingthreats$isRitualTorch() {
		return evolvingthreats$ritualTorch;
	}

	@Override
	public void evolvingthreats$setRitualTorch(boolean torch) {
		this.evolvingthreats$ritualTorch = torch;
	}

	@Override
	public boolean evolvingthreats$ritualExploded() {
		return evolvingthreats$ritualExploded;
	}

	@Override
	public void evolvingthreats$setRitualExploded(boolean exploded) {
		this.evolvingthreats$ritualExploded = exploded;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveRitualSkeleton(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$ritual) {
			return;
		}
		output.putBoolean("EvolvingThreatsRitual", true);
		output.putInt("EvolvingThreatsRitualHead", evolvingthreats$ritualHead);
		output.putBoolean("EvolvingThreatsRitualTorch", evolvingthreats$ritualTorch);
		if (evolvingthreats$ritualGroup != null) {
			output.putString("EvolvingThreatsRitualGroup", evolvingthreats$ritualGroup.toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadRitualSkeleton(ValueInput input, CallbackInfo ci) {
		evolvingthreats$ritual = input.getBooleanOr("EvolvingThreatsRitual", false);
		if (!evolvingthreats$ritual) {
			return;
		}
		evolvingthreats$ritualHead = input.getIntOr("EvolvingThreatsRitualHead", 0);
		evolvingthreats$ritualTorch = input.getBooleanOr("EvolvingThreatsRitualTorch", false);
		input.getString("EvolvingThreatsRitualGroup").ifPresent(raw -> evolvingthreats$ritualGroup = UUID.fromString(raw));
		RitualBand.applyTuning((Mob) (Object) this);
	}
}
