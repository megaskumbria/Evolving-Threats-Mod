package com.example.mixin;

import com.example.RandomArmor;
import com.example.RandomWeapons;
import com.example.beacon.JackLanternBeacon;
import com.example.spawnerhead.SpawnerHeadMob;
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

		// Утопленники (у них трезубец) и зомбифицированные пиглины (золотой меч)
		// тоже наследуются от Zombie - их не трогаем.
		if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
			return;
		}

		if (zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		if (zombie instanceof ZombieOverseerEntity data && data.evolvingthreats$isOverseer()) {
			return;
		}

		if (level instanceof ServerLevel serverLevel && ZombieOverseer.tryCreateOnSpawn(zombie, serverLevel)) {
			return;
		}

		RandomWeapons.tryEquip(zombie, RandomWeapons.ZOMBIE_WEAPON_CHANCE);
		RandomArmor.rollForZombie(zombie);
		// Редкая «голова»: спавнер или jack o'lantern-маяк (один слот HEAD).
		if (!SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
			JackLanternBeacon.tryEquipOnSpawn(zombie);
		}
	}
}
