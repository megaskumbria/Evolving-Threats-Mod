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
 Charms on the weapon of the robber (crossbow) and the thug (axe) when appearing.
 The same code is hung on both classes - both have their finalizeSpawn, after which the weapon is already in hand.
 The robber from the raid is missed here: the game next gives out the crossbow again,
 * and the spell hangs {@link PillagerRaidEnchantMixin} already on him.
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
