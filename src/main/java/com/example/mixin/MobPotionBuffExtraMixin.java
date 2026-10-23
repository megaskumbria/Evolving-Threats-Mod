package com.example.mixin;

import com.example.MobHealth;
import com.example.MobPotionBuffs;
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
 * Общее появление моба. Сюда попадают крипер, эндермен, блейз, фантом, слизень,
 * магмовый куб, страж, древний страж, шалкер, пиглин, брут, хоглин и зоглин.
 * Зомби, скелеты и пауки получают баф в своих mixin.
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
	}
}
