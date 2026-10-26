package com.example.mixin;

import com.example.MobEnchantments;
import com.example.MobPotionBuffs;
import com.example.MobShields;
import com.example.MobTotem;
import com.example.RandomArmor;
import com.example.RandomWeapons;
import com.example.beacon.JackLanternBeacon;
import com.example.siege.SiegeNight;
import com.example.spawnerhead.SpawnerHeadMob;
import com.example.zombie.TntZombie;
import com.example.zombie.ZombieSorcerer;
import com.example.ritual.RitualBand;
import com.example.entity.RaidParticipantEntity;
import com.example.entity.ZombieOverseerEntity;
import com.example.overseer.ZombieOverseer;
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
 * Миксин "вклеивает" наш код в готовый класс Zombie из Minecraft.
 * finalizeSpawn() игра вызывает ровно один раз, когда моб только что появился
 * (а не когда он загружается из сохранения) - то, что нам нужно.
 */
@Mixin(Zombie.class)
public class ZombieMixin {

	// At("RETURN") = выполнить наш код в самом конце метода, когда ванильный код уже отработал.
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$giveRandomWeapon(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {

		Zombie zombie = (Zombie) (Object) this;

		// Зомбифицированный пиглин мирный, пока его не ударят. Ему бафы и оружие мода не выдаём.
		if (zombie instanceof ZombifiedPiglin) {
			return;
		}

		if (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		if (zombie instanceof ZombieOverseerEntity data && data.evolvingthreats$isOverseer()) {
			return;
		}

		// Помощники надсмотрщика приходят как MOB_SUMMONED. Если им тоже давать шанс
		// стать надсмотрщиком, рядом с боем появляется ещё один, и стая плодится.
		// В ночь осады мини-боссы не появляются: надсмотрщик в том числе.
		if (spawnReason != EntitySpawnReason.MOB_SUMMONED
				&& level instanceof ServerLevel serverLevel
				&& !SiegeNight.isWindowOpen(serverLevel)
				&& ZombieOverseer.tryCreateOnSpawn(zombie, serverLevel)) {
			return;
		}

		// Ритуальная стая: своя кожа и голова. Случайное оружие и чары ей не выдаём.
		if (RitualBand.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Зомби, кадавр, зомби-житель и утопленник. Утопленнику дальше оружие не выдаём: у него свой трезубец.
		MobPotionBuffs.tryApply(zombie);
		if (zombie instanceof Drowned) {
			return;
		}

		// Чародей: своя броня и мотыга, без чар. Случайное оружие и голова ему не выдаются.
		if (ZombieSorcerer.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Динамит вместо меча. Блоки не ломает. Броня и зелья остаются.
		if (TntZombie.tryClaim(zombie, spawnReason)) {
			RandomArmor.rollForZombie(zombie);
			MobEnchantments.rollArmorForZombie(zombie);
			if (!SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
				JackLanternBeacon.tryEquipOnSpawn(zombie);
			}
			return;
		}

		// Два оружия занимают обе руки. Тотем и щит тогда не выдаём.
		if (!RandomWeapons.tryEquipDualMelee(zombie, RandomWeapons.ZOMBIE_DUAL_MELEE_CHANCE)) {
			RandomWeapons.tryEquip(zombie, RandomWeapons.ZOMBIE_WEAPON_CHANCE);
			if (!MobTotem.tryEquip(zombie)) {
				MobShields.tryEquip(zombie);
			}
		}
		MobEnchantments.rollForZombie(zombie);
		RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
		// Редкая «голова»: спавнер или jack o'lantern-маяк (один слот HEAD).
		if (!SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
			JackLanternBeacon.tryEquipOnSpawn(zombie);
		}
	}
}
