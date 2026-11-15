package com.example.mixin;

import com.example.sight.GlassSight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla line of sight stops on glass. After a lucky roll, the same check treats glass as air
 * for that one player, so the mob keeps coming and a skeleton can shoot through the window.
 * Other targets, and mobs who have not rolled lucky yet, stay on the vanilla check.
 */
@Mixin(LivingEntity.class)
public class LivingEntityGlassSightMixin {

	@Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$seeThroughGlass(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!(target instanceof Player) || !((Object) this instanceof Mob mob) || !GlassSight.hasNoticed(mob, target)) {
			return;
		}
		cir.setReturnValue(GlassSight.sees(mob, target));
	}
}
