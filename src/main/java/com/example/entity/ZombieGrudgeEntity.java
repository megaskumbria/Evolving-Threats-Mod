package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/*Data "player memory" in zombies. Implementation - {@link com.example.mixin.ZombieGrudgeDataMixin} */
public interface ZombieGrudgeEntity {

	@Nullable
	UUID evolvingthreats$getGrudgePlayerId();

	void evolvingthreats$setGrudgePlayerId(@Nullable UUID playerId);

	long evolvingthreats$getGrudgeStartedGameTime();

	void evolvingthreats$setGrudgeStartedGameTime(long gameTime);
}
