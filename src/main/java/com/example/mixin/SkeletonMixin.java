package com.example.mixin;

import com.example.MobPotionBuffs;
import com.example.entity.RaidParticipantEntity;
import com.example.MobEnchantments;
import com.example.MobShields;
import com.example.MobTotem;
import com.example.RandomArmor;
import com.example.RandomDispenserSkull;
import com.example.RandomWeapons;
import com.example.bastion.Bastion;
import com.example.mirror.Mirror;
import com.example.spiky.Spiky;
import com.example.beacon.JackLanternBeacon;
import com.example.skeleton.BasiliskSkeleton;
import com.example.skeleton.FastBowSkeleton;
import com.example.skeleton.SkeletonCaptain;
import com.example.skeleton.SkeletonHorseman;
import com.example.skeleton.SkeletonPunchBow;
import com.example.skeleton.HarmfulArrowSkeleton;
import com.example.skeleton.RandomArrowSkeleton;
import com.example.skeleton.SmallSkeleton;
import com.example.skeleton.SniperSkeleton;
import com.example.skeleton.TacticianSkeleton;
import com.example.skeleton.WitherSkeletonSword;
import com.example.spawnerhead.SpawnerHeadMob;
import com.example.ritual.RitualBand;
import com.example.threat.ThreatPack;
import net.minecraft.server.level.ServerLevel;
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
		try {

		// Чем выше шкала угрозы, тем чаще к ночному скелету добавляются ещё такие же (ThreatPack).
		if (level instanceof ServerLevel packLevel) {
			ThreatPack.tryAddExtras(skeleton, packLevel, spawnReason);
		}

		// Скелет-иссушитель и так сильный. Бафы и набор мода ему не выдаём.
		// Спавнер на голове — отдельно: каждые 2 удара зовёт двоих таких же.
		if (skeleton instanceof WitherSkeleton wither) {
			// Носитель спавнера меч не меняет. Остальным в аду — незерит и чары.
			if (!SpawnerHeadMob.tryEquipWitherSkeleton(wither)) {
				WitherSkeletonSword.tryApply(wither);
			}
			return;
		}

		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		// Команда уже пометила Бастиона или Spiky. Редкие роли их не забирают: им нужен обычный набор.
		boolean bastion = Bastion.isBastion(skeleton);
		boolean spiky = Spiky.isSpiky(skeleton);

		// Ритуальная стая раньше капитана: свой набор, без лука и без чар.
		if (!bastion && !spiky && skeleton instanceof Skeleton plain && RitualBand.tryClaim(plain, spawnReason)) {
			return;
		}

		// Василиск раньше капитана: свой незерит и лук. Обычный набор мода ему не выдаём.
		if (!bastion && !spiky && BasiliskSkeleton.tryClaim(skeleton, spawnReason)) {
			return;
		}

		// Капитан и его помощники: свой набор и свой отряд. Зелья и случайное оружие им не выдаём.
		if (!bastion && !spiky && SkeletonCaptain.tryClaim(skeleton, level, spawnReason)) {
			return;
		}

		// Обычный скелет, странник, болотный и пустынный. Оружие мода — только у первых двух.
		MobPotionBuffs.tryApply(skeleton);
		if (!(skeleton instanceof Skeleton || skeleton instanceof Stray)) {
			return;
		}

		// «700 IQ» — свой набор: шлем, лук и меч. Остальные варианты скелета ему не выдаём.
		if (!bastion && !spiky && TacticianSkeleton.tryRoll(skeleton)) {
			skeleton.reassessWeaponGoal();
			SkeletonHorseman.tryMount(skeleton, level, spawnReason);
			return;
		}

		// Снайпер раньше маленького и двух мечей: лук ему обязателен.
		boolean sniper = !bastion && !spiky && SniperSkeleton.tryRoll(skeleton);
		// Маленький скелет быстрее и с малым здоровьем. Бастиону, Spiky и снайперу это не выдаём.
		if (!bastion && !spiky && !sniper) {
			bastion = Bastion.tryRoll(skeleton, spawnReason);
		}
		if (!bastion && !spiky && !sniper) {
			spiky = Spiky.tryRoll(skeleton, spawnReason);
		}
		boolean small = !sniper && !bastion && !spiky && SmallSkeleton.tryRoll(skeleton);
		boolean dualMelee = !sniper && !small
				&& RandomWeapons.tryEquipDualMelee(skeleton, RandomWeapons.SKELETON_DUAL_MELEE_CHANCE);
		boolean totem = !sniper && !dualMelee && !small && MobTotem.tryEquip(skeleton);
		boolean handsTaken = dualMelee || totem;
		// Случайные стрелы: лук остаётся, стрелу с эффектом в руку не кладём.
		// Два меча лук убирают — такой скелет этот шанс не бросает.
		if (!sniper && !dualMelee && !bastion && !spiky) {
			RandomArrowSkeleton.tryRoll(skeleton);
		}
		boolean randomArrows = RandomArrowSkeleton.isActive(skeleton);
		// Два оружия ближнего боя убирают лук. Иначе тотем, стрела или меч занимают вторую руку.
		if (!sniper && !handsTaken && !randomArrows && !HarmfulArrowSkeleton.tryEquip(skeleton)) {
			RandomWeapons.rollSkeletonLoadout(skeleton);
			MobShields.tryEquip(skeleton);
		} else if (randomArrows && !totem && !dualMelee) {
			RandomWeapons.rollSkeletonLoadout(skeleton);
			if (skeleton.getOffhandItem().isEmpty()) {
				MobShields.tryEquip(skeleton);
			}
		}
		if (randomArrows) {
			RandomArrowSkeleton.ensureBow(skeleton);
		}
		if (sniper) {
			SniperSkeleton.equip(skeleton);
		}
		MobEnchantments.rollForSkeleton(skeleton);
		// Откидывание V/VI поверх обычных чар. Капитан и «700 IQ» сюда не доходят.
		SkeletonPunchBow.tryApply(skeleton);
		boolean fullSet = RandomArmor.rollForSkeleton(skeleton);
		MobEnchantments.rollArmorForSkeleton(skeleton);
		// Одна «особая» голова: спавнер → маяк (jack) → раздатчик.
		// Полный комплект шлем не отдаёт.
		if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(skeleton)) {
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
		// После брони, чар и лука. Бастион и Spiky вместе не бывают.
		if (bastion) {
			Bastion.apply(skeleton);
		} else if (spiky) {
			Spiky.apply(skeleton);
		}
		} finally {
			// Скелет и скелет-иссушитель. Василиск и капитан уже с именем — шанс их не трогает.
			Mirror.tryRoll(skeleton, spawnReason);
		}
	}
}
