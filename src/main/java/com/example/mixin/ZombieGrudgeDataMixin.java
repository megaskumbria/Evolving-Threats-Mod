package com.example.mixin;

import com.example.entity.ZombieGrudgeEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(Zombie.class)
public class ZombieGrudgeDataMixin implements ZombieGrudgeEntity {

	@Unique
	@Nullable
	private UUID evolvingthreats$grudgePlayerId;

	@Unique
	private long evolvingthreats$grudgeStartedGameTime;

	@Override
	@Nullable
	public UUID evolvingthreats$getGrudgePlayerId() {
		return evolvingthreats$grudgePlayerId;
	}

	@Override
	public void evolvingthreats$setGrudgePlayerId(@Nullable UUID playerId) {
		this.evolvingthreats$grudgePlayerId = playerId;
	}

	@Override
	public long evolvingthreats$getGrudgeStartedGameTime() {
		return evolvingthreats$grudgeStartedGameTime;
	}

	@Override
	public void evolvingthreats$setGrudgeStartedGameTime(long gameTime) {
		this.evolvingthreats$grudgeStartedGameTime = gameTime;
	}
}
