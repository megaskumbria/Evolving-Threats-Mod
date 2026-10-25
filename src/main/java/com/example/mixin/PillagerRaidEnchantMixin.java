package com.example.mixin;

import com.example.MobEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.illager.Pillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рейд сначала выдаёт разбойнику свой арбалет (часто с быстрой перезарядкой I или II).
 * Сразу после этого тот же бросок, что вне рейда: с шансом арбалет получает чары мода,
 * вплоть до максимального уровня. Если бросок не выпал, чара рейда остаётся.
 */
@Mixin(Pillager.class)
public class PillagerRaidEnchantMixin {

	@Inject(method = "applyRaidBuffs", at = @At("RETURN"))
	private void evolvingthreats$enchantRaidCrossbow(ServerLevel level, int wave, boolean captain, CallbackInfo ci) {
		MobEnchantments.rollForIllager((Pillager) (Object) this);
	}
}
