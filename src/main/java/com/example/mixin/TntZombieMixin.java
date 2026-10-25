package com.example.mixin;

import com.example.entity.TntZombieEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Обычный зомби помнит, что его оружие — динамит, и сколько ещё ждать до следующего.
 * Запись в мир здесь: сохранение объявлено у Zombie.
 */
@Mixin(Zombie.class)
public class TntZombieMixin implements TntZombieEntity {

	@Unique
	private boolean evolvingthreats$tntZombie;

	@Unique
	private int evolvingthreats$tntCooldown;

	@Override
	public boolean evolvingthreats$isTntZombie() {
		return evolvingthreats$tntZombie;
	}

	@Override
	public void evolvingthreats$setTntZombie(boolean tntZombie) {
		this.evolvingthreats$tntZombie = tntZombie;
	}

	@Override
	public int evolvingthreats$getTntCooldown() {
		return evolvingthreats$tntCooldown;
	}

	@Override
	public void evolvingthreats$setTntCooldown(int ticks) {
		this.evolvingthreats$tntCooldown = ticks;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveTntZombie(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$tntZombie) {
			return;
		}
		output.putBoolean("EvolvingThreatsTntZombie", true);
		output.putInt("EvolvingThreatsTntCooldown", evolvingthreats$tntCooldown);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadTntZombie(ValueInput input, CallbackInfo ci) {
		evolvingthreats$tntZombie = input.getBooleanOr("EvolvingThreatsTntZombie", false);
		evolvingthreats$tntCooldown = input.getIntOr("EvolvingThreatsTntCooldown", 0);
	}
}
