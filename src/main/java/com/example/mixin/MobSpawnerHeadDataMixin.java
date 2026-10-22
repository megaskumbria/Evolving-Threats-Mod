package com.example.mixin;

import com.example.entity.SpawnerHeadCarrierEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public class MobSpawnerHeadDataMixin implements SpawnerHeadCarrierEntity {

	@Unique
	private boolean evolvingthreats$spawnerHeadCarrier;

	@Unique
	private int evolvingthreats$spawnerHeadHitCount;

	@Unique
	private boolean evolvingthreats$spawnerReinforcement;

	@Override
	public boolean evolvingthreats$isSpawnerHeadCarrier() {
		return evolvingthreats$spawnerHeadCarrier;
	}

	@Override
	public void evolvingthreats$setSpawnerHeadCarrier(boolean carrier) {
		this.evolvingthreats$spawnerHeadCarrier = carrier;
	}

	@Override
	public int evolvingthreats$getSpawnerHeadHitCount() {
		return evolvingthreats$spawnerHeadHitCount;
	}

	@Override
	public void evolvingthreats$setSpawnerHeadHitCount(int count) {
		this.evolvingthreats$spawnerHeadHitCount = count;
	}

	@Override
	public boolean evolvingthreats$isSpawnerReinforcement() {
		return evolvingthreats$spawnerReinforcement;
	}

	@Override
	public void evolvingthreats$setSpawnerReinforcement(boolean reinforcement) {
		this.evolvingthreats$spawnerReinforcement = reinforcement;
	}
}
