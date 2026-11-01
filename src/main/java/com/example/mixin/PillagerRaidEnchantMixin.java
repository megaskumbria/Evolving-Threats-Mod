package com.example.mixin;

import com.example.MobEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.illager.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 The raid first gives the robber his crossbow (often with a quick reload I or II).
 Immediately after that, the same throw as outside the raid: with a chance, the crossbow gets the charm of fashion,
 * up to the maximum level. If the throw does not fall, the charm of the raid remains.
 */
@Mixin(Pillager.class)
public class PillagerRaidEnchantMixin {

	@Inject(method = "applyRaidBuffs", at = @At("RETURN"))
	private void evolvingthreats$enchantRaidCrossbow(ServerLevel level, int wave, boolean captain, CallbackInfo ci) {
		MobEnchantments.rollForIllager((Pillager) (Object) this);
	}
}
