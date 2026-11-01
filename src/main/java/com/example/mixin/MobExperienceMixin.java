package com.example.mixin;

import com.example.MobExperience;
import com.example.bastion.Bastion;
import com.example.skeleton.WitherSkeletonSword;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Doubles the experience of any hostile mob. The game calls this method at death.
 Zombies have their own method: first he counts the experience, then calls this.
 Therefore, the multiplier is here, not in each zombie separately.
 */
@Mixin(Mob.class)
public class MobExperienceMixin {

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$bonusXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		Mob mob = (Mob) (Object) this;
		// His Bastion number. Otherwise, the total multiplier would double below it.
		int bastion = Bastion.experienceReward(mob);
		if (bastion >= 0) {
			cir.setReturnValue(bastion);
			return;
		}
		// Your dryer number. Otherwise, the total multiplier would double below it.
		int wither = WitherSkeletonSword.experienceReward(mob);
		if (wither >= 0) {
			cir.setReturnValue(wither);
			return;
		}
		int xp = MobExperience.reward(mob, cir.getReturnValue());
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}
}
