package com.example.mixin;

import com.example.entity.ai.ZombieSorcererSpellGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spell goal is registered on all zombies but only runs for sorcerers.
 * Sorcerers do not break blocks: {@link com.example.digging.BlockBreaking#isBreaker} decides that.
 */
@Mixin(Zombie.class)
public class ZombieSorcererGoalMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$sorcererSpell(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(3, new ZombieSorcererSpellGoal(self));
	}
}
