package com.example.mixin;

import com.example.MobPotionBuffs;
import com.example.entity.RaidParticipantEntity;
import com.example.MobEnchantments;
import com.example.MobShields;
import com.example.MobTotem;
import com.example.RandomArmor;
import com.example.RandomDispenserSkull;
import com.example.RandomWeapons;
import com.example.beacon.JackLanternBeacon;
import com.example.skeleton.FastBowSkeleton;
import com.example.skeleton.SkeletonCaptain;
import com.example.skeleton.SkeletonHorseman;
import com.example.skeleton.SkeletonPunchBow;
import com.example.skeleton.HarmfulArrowSkeleton;
import com.example.skeleton.SmallSkeleton;
import com.example.skeleton.TacticianSkeleton;
import com.example.spawnerhead.SpawnerHeadMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
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

		// Скелет-иссушитель и так сильный. Бафы и набор мода ему не выдаём.
		// Спавнер на голове — отдельно: каждые 2 удара зовёт двоих таких же.
		if (skeleton instanceof WitherSkeleton) {
			SpawnerHeadMob.tryEquipWitherSkeleton(skeleton);
			return;
		}

		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		// Капитан и его помощники: свой набор и свой отряд. Зелья и случайное оружие им не выдаём.
		if (SkeletonCaptain.tryClaim(skeleton, level, spawnReason)) {
			return;
		}

		// Обычный скелет, странник, болотный и пустынный. Оружие мода — только у первых двух.
		MobPotionBuffs.tryApply(skeleton);
		if (!(skeleton instanceof Skeleton || skeleton instanceof Stray)) {
			return;
		}

		// «700 IQ» — свой набор: шлем, лук и меч. Остальные варианты скелета ему не выдаём.
		if (TacticianSkeleton.tryRoll(skeleton)) {
			skeleton.reassessWeaponGoal();
			SkeletonHorseman.tryMount(skeleton, level, spawnReason);
			return;
		}

		// Маленький скелет: без двух оружий и тотема. Лук остаётся обычным.
		boolean small = SmallSkeleton.tryRoll(skeleton);
		boolean handsTaken = !small
				&& (RandomWeapons.tryEquipDualMelee(skeleton, RandomWeapons.SKELETON_DUAL_MELEE_CHANCE)
						|| MobTotem.tryEquip(skeleton));
		// Два оружия ближнего боя убирают лук. Иначе тотем, стрела или меч занимают вторую руку.
		if (!handsTaken && !HarmfulArrowSkeleton.tryEquip(skeleton)) {
			RandomWeapons.rollSkeletonLoadout(skeleton);
			MobShields.tryEquip(skeleton);
		}
		MobEnchantments.rollForSkeleton(skeleton);
		// Откидывание V/VI поверх обычных чар. Капитан и «700 IQ» сюда не доходят.
		SkeletonPunchBow.tryApply(skeleton);
		RandomArmor.rollForSkeleton(skeleton);
		MobEnchantments.rollArmorForSkeleton(skeleton);
		// Одна «особая» голова: спавнер → маяк (jack) → раздатчик.
		if (!SpawnerHeadMob.tryEquipOnSpawn(skeleton)) {
			if (!JackLanternBeacon.tryEquipOnSpawn(skeleton)) {
				RandomDispenserSkull.tryEquipDispenserHead(skeleton);
			}
		}
		// Дальний лук только у взрослого и только если лук остался.
		if (!small) {
			FastBowSkeleton.tryRoll(skeleton);
		}
		skeleton.reassessWeaponGoal();
		SkeletonHorseman.tryMount(skeleton, level, spawnReason);
	}
}
