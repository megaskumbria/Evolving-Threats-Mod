package com.example.mixin;

import com.example.entity.ZombieOverseerEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(Zombie.class)
public class ZombieOverseerDataMixin implements ZombieOverseerEntity {

	@Unique
	private boolean evolvingthreats$overseer;

	@Unique
	@Nullable
	private UUID evolvingthreats$huntTargetId;

	@Unique
	private long evolvingthreats$huntStartedGameTime;

	@Unique
	private int evolvingthreats$activeServantCount;

	@Unique
	private int evolvingthreats$summonCooldownTicks;

	@Override
	public boolean evolvingthreats$isOverseer() {
		return evolvingthreats$overseer;
	}

	@Override
	public void evolvingthreats$setOverseer(boolean overseer) {
		this.evolvingthreats$overseer = overseer;
	}

	@Override
	@Nullable
	public UUID evolvingthreats$getHuntTargetId() {
		return evolvingthreats$huntTargetId;
	}

	@Override
	public void evolvingthreats$setHuntTargetId(@Nullable UUID playerId) {
		this.evolvingthreats$huntTargetId = playerId;
	}

	@Override
	public long evolvingthreats$getHuntStartedGameTime() {
		return evolvingthreats$huntStartedGameTime;
	}

	@Override
	public void evolvingthreats$setHuntStartedGameTime(long gameTime) {
		this.evolvingthreats$huntStartedGameTime = gameTime;
	}

	@Override
	public int evolvingthreats$getActiveServantCount() {
		return evolvingthreats$activeServantCount;
	}

	@Override
	public void evolvingthreats$setActiveServantCount(int count) {
		this.evolvingthreats$activeServantCount = count;
	}

	@Override
	public int evolvingthreats$getSummonCooldownTicks() {
		return evolvingthreats$summonCooldownTicks;
	}

	@Override
	public void evolvingthreats$setSummonCooldownTicks(int ticks) {
		this.evolvingthreats$summonCooldownTicks = ticks;
	}

}
