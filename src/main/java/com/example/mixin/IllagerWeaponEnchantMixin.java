package com.example.mixin;

import com.example.MobEnchantments;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Чары на оружии разбойника (арбалет) и громилы (топор) при появлении.
 * Один и тот же код вешается на оба класса — у обоих свой finalizeSpawn, после которого оружие уже в руке.
 * Разбойник из рейда здесь пропускается: игра следом выдаёт арбалет заново,
 * и чары вешает {@link PillagerRaidEnchantMixin} уже на него.
 */
@Mixin({ Pillager.class, Vindicator.class })
public class IllagerWeaponEnchantMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$enchantIllagerWeapon(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Mob mob = (Mob) (Object) this;
		if (mob instanceof Pillager pillager && pillager.getCurrentRaid() != null) {
			return;
		}
		MobEnchantments.rollForIllager(mob);
	}
}
