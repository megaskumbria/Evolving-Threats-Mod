package com.example.mixin;

import com.example.MobEnchantments;
import com.example.MobPotionBuffs;
import com.example.MobShields;
import com.example.MobTotem;
import com.example.RandomArmor;
import com.example.RandomWeapons;
import com.example.bastion.Bastion;
import com.example.mirror.Mirror;
import com.example.spiky.Spiky;
import com.example.beacon.JackLanternBeacon;
import com.example.siege.SiegeNight;
import com.example.spawnerhead.SpawnerHeadMob;
import com.example.zombie.TntZombie;
import com.example.zombie.ZombieExcalibur;
import com.example.zombie.ZombieMjolnir;
import com.example.zombie.ZombieSorcerer;
import com.example.ritual.RitualBand;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieExcaliburEntity;
import com.example.entity.ZombieMjolnirEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
import com.example.raid.RaidAnchorNatural;
import com.example.threat.ThreatPack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Mixin sticks our code into the ready-made Zombie class from Minecraft.
 FinalizeSpawn() The game calls exactly once the mob has just appeared
 * (not when it is downloaded from save) is what we need.
 */
@Mixin(Zombie.class)
public class ZombieMixin {

	// At("RETURN") = execute our code at the very end of the method when the vanilla code has already worked.
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$giveRandomWeapon(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {

		Zombie zombie = (Zombie) (Object) this;
		try {

		// The zombie piglin is peaceful until it is hit. We don't give him buffs and guns.
		if (zombie instanceof ZombifiedPiglin) {
			return;
		}

		// Clump of extra zombies beside this one. Off unless ThreatPack.ENABLED (HostileSpawnCap raises the night limit instead).
		if (level instanceof ServerLevel packLevel) {
			ThreatPack.tryAddExtras(zombie, packLevel, spawnReason);
		}

		// The team has already tagged Bastion or Spiky. Rare roles don't take them away: they need a regular set.
		boolean bastion = Bastion.isBastion(zombie);
		boolean spiky = Spiky.isSpiky(zombie);

		if (!bastion && !spiky && zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		if (!bastion && !spiky && zombie instanceof ZombieOverseerEntity data && data.evolvingthreats$isOverseer()) {
			return;
		}

		// The team has already tagged him. The overseer, anchor and wizard of this zombie are not taken away.
		if (!bastion && !spiky && zombie instanceof ZombieExcaliburEntity excalibur && excalibur.evolvingthreats$isExcalibur()) {
			MobPotionBuffs.tryApply(zombie);
			ZombieExcalibur.tryClaim(zombie, spawnReason);
			return;
		}

		// The team has already tagged him Mjölnir. The overseer, anchor and wizard of this zombie are not taken away.
		if (!bastion && !spiky && zombie instanceof ZombieMjolnirEntity mjolnir && mjolnir.evolvingthreats$isMjolnir()) {
			MobPotionBuffs.tryApply(zombie);
			ZombieMjolnir.tryClaim(zombie, spawnReason);
			return;
		}

		// The superintendents come as MOB SUMMONED. If you give them a chance, too
		// Become an overseer, next to the fight, another appears, and the pack breeds.
		// On the night of the siege, mini-bosses do not appear, including the overseer.
		if (!bastion && !spiky
				&& spawnReason != EntitySpawnReason.MOB_SUMMONED
				&& level instanceof ServerLevel serverLevel
				&& !SiegeNight.isWindowOpen(serverLevel)
				&& ZombieOverseer.tryCreateOnSpawn(zombie, serverLevel)) {
			return;
		}

		// Raid anchor (from the 80th day, very rarely, at night on the surface). Details at RaidAnchorNatural.
		if (!bastion && !spiky
				&& level instanceof ServerLevel serverLevel
				&& RaidAnchorNatural.tryConvertOnSpawn(zombie, serverLevel, spawnReason)) {
			return;
		}

		// Ritual pack: its own skin and head. We do not give her accidental weapons and charms.
		if (!bastion && !spiky && RitualBand.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Zombie, husk, zombie villager and drowned. The drowned man is not given a weapon: he has his own trident.
		MobPotionBuffs.tryApply(zombie);
		if (zombie instanceof Drowned) {
			return;
		}

		// Sorcerer: his armor and hoe, without enchantment. He is not given an accidental weapon or head.
		if (!bastion && !spiky && ZombieSorcerer.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Excalibur: Sword with sharpness 20 always. Armor, armor charms and shield - inside tryClaim, like a normal zombie.
		if (!bastion && !spiky && ZombieExcalibur.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Mjölnir: mace with bestowal 20. He is not given random weapons, shields and heads. Armor is like a normal zombie.
		if (!bastion && !spiky && ZombieMjolnir.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Dynamite instead of sword. The blocks don't break. Armor and potions remain.
		if (!bastion && !spiky && TntZombie.tryClaim(zombie, spawnReason)) {
			boolean fullSet = RandomArmor.rollForZombie(zombie);
			MobEnchantments.rollArmorForZombie(zombie);
			// The full set does not take the helmet: otherwise a set of four things is not visible.
			if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
				JackLanternBeacon.tryEquipOnSpawn(zombie);
			}
			return;
		}

		// Two weapons occupy both hands. The totem and shield won't come out.
		if (!RandomWeapons.tryEquipDualMelee(zombie, RandomWeapons.ZOMBIE_DUAL_MELEE_CHANCE)) {
			RandomWeapons.tryEquip(zombie, RandomWeapons.ZOMBIE_WEAPON_CHANCE);
			if (!MobTotem.tryEquip(zombie)) {
				MobShields.tryEquip(zombie);
			}
		}
		MobEnchantments.rollForZombie(zombie);
		boolean fullSet = RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
		// Rare "head": spawner or jack o'lantern beacon (one HEAD slot).
		// The full set doesn't give up.
		if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
			JackLanternBeacon.tryEquipOnSpawn(zombie);
		}
		// After armor and charm. Bastion and Spiky are not together: first Bastion, otherwise Spiky.
		if (!spiky && (bastion || Bastion.tryRoll(zombie, spawnReason))) {
			Bastion.apply(zombie);
		} else if (spiky || Spiky.tryRoll(zombie, spawnReason)) {
			Spiky.apply(zombie);
		}
		} finally {
			// After all the early exits: Bastion and the rest of the names have already put a name and this chance is missed.
			Mirror.tryRoll(zombie, spawnReason);
		}
	}
}
