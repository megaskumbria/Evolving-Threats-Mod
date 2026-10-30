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
		try {

		// Зомбифицированный пиглин мирный, пока его не ударят. Ему бафы и оружие мода не выдаём.
		if (zombie instanceof ZombifiedPiglin) {
			return;
		}

		// Чем выше шкала угрозы, тем чаще к ночному зомби добавляются ещё такие же (ThreatPack).
		if (level instanceof ServerLevel packLevel) {
			ThreatPack.tryAddExtras(zombie, packLevel, spawnReason);
		}

		// Команда уже пометила Бастиона или Spiky. Редкие роли их не забирают: им нужен обычный набор.
		boolean bastion = Bastion.isBastion(zombie);
		boolean spiky = Spiky.isSpiky(zombie);

		if (!bastion && !spiky && zombie instanceof RaidParticipantEntity raid && raid.evolvingthreats$isRaidParticipant()) {
			return;
		}

		if (!bastion && !spiky && zombie instanceof ZombieOverseerEntity data && data.evolvingthreats$isOverseer()) {
			return;
		}

		// Команда уже пометила его. Надсмотрщик, якорь и чародей этого зомби не забирают.
		if (!bastion && !spiky && zombie instanceof ZombieExcaliburEntity excalibur && excalibur.evolvingthreats$isExcalibur()) {
			MobPotionBuffs.tryApply(zombie);
			ZombieExcalibur.tryClaim(zombie, spawnReason);
			return;
		}

		// Команда уже пометила его Mjölnir. Надсмотрщик, якорь и чародей этого зомби не забирают.
		if (!bastion && !spiky && zombie instanceof ZombieMjolnirEntity mjolnir && mjolnir.evolvingthreats$isMjolnir()) {
			MobPotionBuffs.tryApply(zombie);
			ZombieMjolnir.tryClaim(zombie, spawnReason);
			return;
		}

		// Помощники надсмотрщика приходят как MOB_SUMMONED. Если им тоже давать шанс
		// стать надсмотрщиком, рядом с боем появляется ещё один, и стая плодится.
		// В ночь осады мини-боссы не появляются: надсмотрщик в том числе.
		if (!bastion && !spiky
				&& spawnReason != EntitySpawnReason.MOB_SUMMONED
				&& level instanceof ServerLevel serverLevel
				&& !SiegeNight.isWindowOpen(serverLevel)
				&& ZombieOverseer.tryCreateOnSpawn(zombie, serverLevel)) {
			return;
		}

		// Рейдовый якорь (с 80-го дня, очень редко, ночью на поверхности). Подробности в RaidAnchorNatural.
		if (!bastion && !spiky
				&& level instanceof ServerLevel serverLevel
				&& RaidAnchorNatural.tryConvertOnSpawn(zombie, serverLevel, spawnReason)) {
			return;
		}

		// Ритуальная стая: своя кожа и голова. Случайное оружие и чары ей не выдаём.
		if (!bastion && !spiky && RitualBand.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Зомби, кадавр, зомби-житель и утопленник. Утопленнику дальше оружие не выдаём: у него свой трезубец.
		MobPotionBuffs.tryApply(zombie);
		if (zombie instanceof Drowned) {
			return;
		}

		// Чародей: своя броня и мотыга, без чар. Случайное оружие и голова ему не выдаются.
		if (!bastion && !spiky && ZombieSorcerer.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Excalibur: меч с Остротой 20 всегда. Броня, чары брони и щит — внутри tryClaim, как у обычного зомби.
		if (!bastion && !spiky && ZombieExcalibur.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Mjölnir: булава с Отдачей 20. Случайные оружие, щит и голова ему не выдаются. Броня — как у обычного зомби.
		if (!bastion && !spiky && ZombieMjolnir.tryClaim(zombie, spawnReason)) {
			return;
		}

		// Динамит вместо меча. Блоки не ломает. Броня и зелья остаются.
		if (!bastion && !spiky && TntZombie.tryClaim(zombie, spawnReason)) {
			boolean fullSet = RandomArmor.rollForZombie(zombie);
			MobEnchantments.rollArmorForZombie(zombie);
			// У полного комплекта шлем не забираем: иначе сета из четырёх вещей не видно.
			if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
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
		boolean fullSet = RandomArmor.rollForZombie(zombie);
		MobEnchantments.rollArmorForZombie(zombie);
		// Редкая «голова»: спавнер или jack o'lantern-маяк (один слот HEAD).
		// Полный комплект шлем не отдаёт.
		if (!fullSet && !SpawnerHeadMob.tryEquipOnSpawn(zombie)) {
			JackLanternBeacon.tryEquipOnSpawn(zombie);
		}
		// После брони и чар. Бастион и Spiky вместе не бывают: сначала Бастион, иначе Spiky.
		if (!spiky && (bastion || Bastion.tryRoll(zombie, spawnReason))) {
			Bastion.apply(zombie);
		} else if (spiky || Spiky.tryRoll(zombie, spawnReason)) {
			Spiky.apply(zombie);
		}
		} finally {
			// После всех ранних выходов: Бастион и остальные именные уже поставили имя и этот шанс пропускают.
			Mirror.tryRoll(zombie, spawnReason);
		}
	}
}
