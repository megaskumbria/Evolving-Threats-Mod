package com.example.mixin;

import com.example.MobPotionBuffs;
import com.example.mirror.Mirror;
import com.example.entity.SpiderRoleEntity;
import com.example.entity.ai.SpiderWebWeaveGoal;
import com.example.spider.SpiderThreats;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Паук: хранит роль (ткач/матка), бросает шанс при появлении (включая рой) и подключает цель «паутина под ноги».
 * Сохранение роли в мире — {@link SpiderRoleSaveMixin}.
 */
@Mixin(Spider.class)
public class SpiderRoleMixin implements SpiderRoleEntity {

	@Unique
	private int evolvingthreats$spiderRole;

	@Override
	public int evolvingthreats$getSpiderRole() {
		return evolvingthreats$spiderRole;
	}

	@Override
	public void evolvingthreats$setSpiderRole(int role) {
		this.evolvingthreats$spiderRole = role;
	}

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollSpiderRole(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Spider spider = (Spider) (Object) this;
		SpiderThreats.rollOnSpawn(spider, level, spawnReason);
		MobPotionBuffs.tryApply(spider);
		// Пещерный паук сюда тоже заходит, но шанс у него не бросается: в списке только обычный паук.
		Mirror.tryRoll(spider, spawnReason);
	}

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerWeaveGoal(CallbackInfo ci) {
		Spider self = (Spider) (Object) this;
		self.getGoalSelector().addGoal(3, new SpiderWebWeaveGoal(self));
	}
}
