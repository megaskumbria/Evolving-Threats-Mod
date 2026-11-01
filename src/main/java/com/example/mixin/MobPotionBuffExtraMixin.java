package com.example.mixin;

import com.example.MobHealth;
import com.example.MobPotionBuffs;
import com.example.mirror.Mirror;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The general appearance of the mob. Here comes the cryper, the enderman, the blaze, the phantom, the slug,
 magma cube, guard, ancient guard, shalker, piglin, brute, hoglin and zoglin.
 Zombies, skeletons and spiders get a buff in their mixin.
 */
@Mixin(Mob.class)
public class MobPotionBuffExtraMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$extraPotionBuffs(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Mob mob = (Mob) (Object) this;
		MobHealth.tryRoll(mob);
		MobPotionBuffs.tryApplyExtra(mob);
		// Ifrit, witch, cryper, piglin, enderman, scales.
		// Zombies, skeleton, spider and brute throw a chance at themselves, otherwise two shots would come out.
		Mirror.tryRollSharedSpawn(mob, spawnReason);
	}
}
