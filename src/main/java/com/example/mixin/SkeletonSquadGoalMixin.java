package com.example.mixin;

import com.example.entity.SkeletonSquadMember;
import com.example.entity.ai.CaptainHuntGoal;
import com.example.entity.ai.SkeletonSquadLeashGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The lead of the squad is hung on all ordinary skeletons, but remains silent until the skeleton is captain and assistant.
 The target is registered here: the wanderer and dryer have their registerGoals, but the list of goals is shared.
 */
@Mixin(AbstractSkeleton.class)
public class SkeletonSquadGoalMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$squadLeash(CallbackInfo ci) {
		if (!((Object) this instanceof Skeleton skeleton) || !((Object) this instanceof SkeletonSquadMember squad)) {
			return;
		}
		skeleton.getGoalSelector().addGoal(1, new CaptainHuntGoal(skeleton, squad));
		skeleton.getGoalSelector().addGoal(2, new SkeletonSquadLeashGoal(skeleton, squad));
	}
}
