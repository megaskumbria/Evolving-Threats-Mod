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

		// Clump of extra skeletons beside this one. Off unless ThreatPack.ENABLED (HostileSpawnCap raises the night limit instead).
		if (level instanceof ServerLevel packLevel) {
			ThreatPack.tryAddExtras(skeleton, packLevel, spawnReason);
		}

		// The dehumidifier skeleton is already strong. We don't give him buffs and fashion sets.
		// Spavner on the head - separately: every 2 strokes calls two of the same.
		if (skeleton instanceof WitherSkeleton wither) {
			// The spawner does not change the sword. The rest of us in hell are enchanted and enchanted.
			if (!SpawnerHeadMob.tryEquipWitherSkeleton(wither)) {
				WitherSkeletonSword.tryApply(wither);
			}
			return;
		}

		if (skeleton instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		// The team has already tagged Bastion or Spiky. Rare roles don't take them away: they need a regular set.
		boolean bastion = Bastion.isBastion(skeleton);
		boolean spiky = Spiky.isSpiky(skeleton);

		// Ritual pack before captain: his set, without bow and without charm.
		if (!bastion && !spiky && skeleton instanceof Skeleton plain && RitualBand.tryClaim(plain, spawnReason)) {
			return;
		}

		// Basilisk before the captain: his nezerite and bow. We don't give him the usual fashion set.
		if (!bastion && !spiky && BasiliskSkeleton.tryClaim(skeleton, spawnReason)) {
			return;
		}

		// The captain and his assistants: his set and his squad. We don't give them potions and random weapons.
		if (!bastion && !spiky && SkeletonCaptain.tryClaim(skeleton, level, spawnReason)) {
			return;
		}

		// Ordinary skeleton, wanderer, marsh and desert. Weapon fashion - only the first two.
		MobPotionBuffs.tryApply(skeleton);
		if (!(skeleton instanceof Skeleton || skeleton instanceof Stray)) {
			return;
		}

		// “700 IQ” – his set: helmet, bow and sword. The rest of the skeleton is not given to him.
		if (!bastion && !spiky && TacticianSkeleton.tryRoll(skeleton)) {
			skeleton.reassessWeaponGoal();
			SkeletonHorseman.tryMount(skeleton, level, spawnReason);
			return;
		}

		// A sniper before a small sword and two swords: a bow is mandatory for him.
		boolean sniper = !bastion && !spiky && SniperSkeleton.tryRoll(skeleton);
		// A small skeleton is faster and in poor health. Bastion, Spiky and a sniper can't do that.
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
		// Accidental arrows: the bow remains, the arrow with effect is not placed in the hand.
		// Two swords are removed from the bow - this skeleton does not give this chance.
		if (!sniper && !dualMelee && !bastion && !spiky) {
			RandomArrowSkeleton.tryRoll(skeleton);
		}
		boolean randomArrows = RandomArrowSkeleton.isActive(skeleton);
		// Two melee weapons remove the bow. The totem, arrow, or sword occupy the other hand.
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
		// Rejection of V/VI over normal spells. The captain and 700 IQ don't get there.
		SkeletonPunchBow.tryApply(skeleton);
		boolean fullSet = RandomArmor.rollForSkeleton(skeleton);
		MobEnchantments.rollArmorForSkeleton(skeleton);
		// One “special” head: spawner → beacon (jack) → distributor.
		// The full set doesn't give up.
		if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(skeleton)) {
			if (!JackLanternBeacon.tryEquipOnSpawn(skeleton)) {
				RandomDispenserSkull.tryEquipDispenserHead(skeleton);
			}
		}
		// Long-distance onions only in adults and only if the onions remain.
		if (!small) {
			FastBowSkeleton.tryRoll(skeleton);
		}
		skeleton.reassessWeaponGoal();
		SkeletonHorseman.tryMount(skeleton, level, spawnReason);
		// After armor, charm and bow. Bastion and Spiky aren't together.
		if (bastion) {
			Bastion.apply(skeleton);
		} else if (spiky) {
			Spiky.apply(skeleton);
		}
		} finally {
			// Skeleton and exhumerator skeleton. Basilisk and the captain already have a name - the chance does not touch them.
			Mirror.tryRoll(skeleton, spawnReason);
		}
	}
}
