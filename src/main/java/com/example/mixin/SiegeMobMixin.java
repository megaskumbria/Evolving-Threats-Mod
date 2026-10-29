package com.example.mixin;

import com.example.entity.ai.SiegeMarchGoal;
import com.example.entity.ai.SiegeTargetGoal;
import com.example.siege.SiegeComposition;
import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Связка осады для всех мобов сразу. Два места:
 *
 * 1) Создание моба. Крипер, паук, ведьма, слизень, фантом, разбойник, поборник, вызыватель
 *    и разоритель получают те же цели, что зомби и скелеты: держать игрока целью и идти к нему.
 *    Какому мобу что нужно — решает {@link SiegeComposition#aiModeOf}. Зомби и скелеты сюда не попадают,
 *    у них своя связка (ZombieRaidGoalsMixin, AbstractSkeletonRaidGoalsMixin).
 *    Цели молчат, пока у моба нет метки осады, так что обычные мобы не меняются.
 *
 * 2) Появление моба (finalizeSpawn). С предупреждения и до рассвета каждый моб, что появился сам
 *    (ночной спавн игры) и входит в состав этой осады, получает метку осады и идёт к игроку.
 *    Появились они командой, спавнером, яйцом или как помощники — не трогаем: это не осада.
 *    Подмена обычных монстров на мобов осады — в NaturalMonsterSwap.
 */
@Mixin(Mob.class)
public class SiegeMobMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void evolvingthreats$siegeGoals(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
		if (level == null || level.isClientSide()) {
			return;
		}
		Mob self = (Mob) (Object) this;
		SiegeComposition.AiMode mode = SiegeComposition.aiModeOf(self);
		if (mode == SiegeComposition.AiMode.NONE) {
			return;
		}
		((MobTargetSelectorAccessor) self).evolvingthreats$getTargetSelector().addGoal(0, new SiegeTargetGoal(self));
		if (mode == SiegeComposition.AiMode.TARGET_AND_MARCH) {
			self.getGoalSelector().addGoal(1, new SiegeMarchGoal(self));
		}
	}

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$siegeEnlist(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if (spawnReason != EntitySpawnReason.NATURAL) {
			return;
		}
		Mob mob = (Mob) (Object) this;
		if (SiegeNight.isWindowOpen(mob.level())
				&& mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& SiegeComposition.fitsCurrent(serverLevel, mob.getType())) {
			SiegeWaves.enlist(mob);
		}
	}
}
