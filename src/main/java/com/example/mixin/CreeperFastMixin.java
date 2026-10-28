package com.example.mixin;

import com.example.creeper.FastCreeper;
import com.example.entity.FastCreeperEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Крипер помнит, что он супербыстрый, и после загрузки мира снова получает скорость.
 * Шанс при появлении — в {@link MobDynamiteCreeperSpawnMixin}.
 */
@Mixin(Creeper.class)
public class CreeperFastMixin implements FastCreeperEntity {

	@Unique
	private boolean evolvingthreats$fastCreeper;

	@Override
	public boolean evolvingthreats$isFastCreeper() {
		return evolvingthreats$fastCreeper;
	}

	@Override
	public void evolvingthreats$setFastCreeper(boolean fast) {
		this.evolvingthreats$fastCreeper = fast;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveFastCreeper(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$fastCreeper) {
			output.putBoolean("EvolvingThreatsFastCreeper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadFastCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$fastCreeper = input.getBooleanOr("EvolvingThreatsFastCreeper", false);
		if (evolvingthreats$fastCreeper) {
			FastCreeper.apply((Creeper) (Object) this);
		}
	}
}
