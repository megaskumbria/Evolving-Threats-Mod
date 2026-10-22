package com.example.mixin;

import com.example.entity.ShieldBreakCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Крипер: флаг «ломает щит» и сохранение.
 * Шанс при появлении — в {@link MobDynamiteCreeperSpawnMixin}, после подрывника и метателя.
 * Ведёт себя как обычный крипер: сам подбегает и взрывается. Щит ломается в момент блока взрыва.
 */
@Mixin(Creeper.class)
public class CreeperShieldBreakMixin implements ShieldBreakCreeperEntity {

	@Unique
	private boolean evolvingthreats$shieldBreakCreeper;

	@Override
	public boolean evolvingthreats$isShieldBreakCreeper() {
		return evolvingthreats$shieldBreakCreeper;
	}

	@Override
	public void evolvingthreats$setShieldBreakCreeper(boolean shieldBreak) {
		this.evolvingthreats$shieldBreakCreeper = shieldBreak;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveShieldBreakCreeper(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$shieldBreakCreeper) {
			output.putBoolean("EvolvingThreatsShieldBreakCreeper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadShieldBreakCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$shieldBreakCreeper = input.getBooleanOr("EvolvingThreatsShieldBreakCreeper", false);
	}
}
