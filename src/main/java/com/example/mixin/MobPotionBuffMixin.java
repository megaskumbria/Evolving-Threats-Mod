package com.example.mixin;

import com.example.MobHealth;
import com.example.MobPotionBuffs;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ведьма, разбойник, громила, вызыватель и разоритель появляются через общий код рейдеров.
 * Иллюзор здесь не проходит. Пещерный паук не вызывает код обычного паука.
 */
@Mixin({ Raider.class, CaveSpider.class })
public class MobPotionBuffMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$potionBuffs(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Mob mob = (Mob) (Object) this;
		// Пещерный паук не вызывает общий спавн, поэтому здоровье бросается здесь.
		if (mob instanceof CaveSpider) {
			MobHealth.tryRoll(mob);
		}
		if (mob instanceof Raider
				&& !(mob instanceof Witch
				|| mob instanceof Pillager
				|| mob instanceof Vindicator
				|| mob instanceof Evoker
				|| mob instanceof Ravager)) {
			return;
		}
		MobPotionBuffs.tryApply(mob);
	}
}
