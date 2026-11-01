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
 Bundle of siege for all mobs at once. Two seats:
 *
 * (1) Making a mob. Creeper, spider, witch, phantom, robber, champion, summoner
 And the destroyer gets the same goals as zombies and skeletons: keep the player a target and go to him.
 * Which mob needs to be decided by {@link SiegeComposition#aiModeOf}. Zombies and skeletons don't get in here.
 They have their own bundle (ZombieRaidGoalsMixin, AbstractSkeletonRaidGoalsMixin).
 Targets are silent until the mob has a siege mark, so the usual mobs do not change.
 *
 * (2) The appearance of the mob (finalizeSpawn). From the warning until dawn, every mob that appeared
 * (Night Spawn game) and is part of this siege, receives the mark of the siege and goes to the player.
 They appeared as a team, a spawner, an egg or as assistants - we do not touch: this is not a siege.
 Substituting ordinary monsters for mobs of siege - in NaturalMonsterSwap.
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
