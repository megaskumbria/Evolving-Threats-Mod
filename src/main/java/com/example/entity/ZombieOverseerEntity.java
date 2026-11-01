package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 Data mini-boss "Rotten Overseer" on the usual zombie.
 * Implementation - {@link com.example.mixin.ZombieOverseerDataMixin}.
 */
public interface ZombieOverseerEntity {

	boolean evolvingthreats$isOverseer();

	void evolvingthreats$setOverseer(boolean overseer);

	@Nullable
	UUID evolvingthreats$getHuntTargetId();

	void evolvingthreats$setHuntTargetId(@Nullable UUID playerId);

	long evolvingthreats$getHuntStartedGameTime();

	void evolvingthreats$setHuntStartedGameTime(long gameTime);

	int evolvingthreats$getActiveServantCount();

	void evolvingthreats$setActiveServantCount(int count);

	int evolvingthreats$getSummonCooldownTicks();

	void evolvingthreats$setSummonCooldownTicks(int ticks);
}
