package com.example.mixin;

import com.example.sight.GlassSight;
import com.example.sight.MobSight;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gaze range is set when appearing, to special mobs.
 The overseer, the siege and the quick cryper then put their number if it is larger.
 */
@Mixin(Mob.class)
public class MobSightMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$raiseShortSight(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		MobSight.raiseShortSight((Mob) (Object) this);
	}

	/** Once a server step. The glass roll itself waits 30 seconds inside GlassSight. */
	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void evolvingthreats$glassSightRoll(CallbackInfo ci) {
		GlassSight.tick((Mob) (Object) this);
	}
}
