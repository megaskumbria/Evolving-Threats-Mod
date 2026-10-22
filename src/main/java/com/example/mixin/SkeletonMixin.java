package com.example.mixin;

import com.example.entity.RaidParticipantEntity;
import com.example.RandomArmor;
import com.example.RandomDispenserSkull;
import com.example.RandomWeapons;
import com.example.beacon.JackLanternBeacon;
import com.example.spawnerhead.SpawnerHeadMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeleton.class)
public class SkeletonMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$giveRandomWeapon(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {

		AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;

		if (!(skeleton instanceof Skeleton || skeleton instanceof Stray)) {
			return;
		}

		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		RandomWeapons.rollSkeletonLoadout(skeleton);
		RandomArmor.rollForSkeleton(skeleton);
		// Одна «особая» голова: спавнер → маяк (jack) → раздатчик.
		if (!SpawnerHeadMob.tryEquipOnSpawn(skeleton)) {
			if (!JackLanternBeacon.tryEquipOnSpawn(skeleton)) {
				RandomDispenserSkull.tryEquipDispenserHead(skeleton);
			}
		}
		// Пересчитать цели ИИ: лук / ближний бой / оба (см. AbstractSkeletonDualWieldMixin).
		skeleton.reassessWeaponGoal();
	}
}
