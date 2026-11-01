package com.example.entity;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 A member of the squad who goes to a memorable point, not after the player.
 Implementation - mixin on all raiders (robber, champion, destroyer).
 */
public interface IllagerPatrolMember {

	@Nullable
	BlockPos evolvingthreats$getPatrolDestination();

	void evolvingthreats$setPatrolDestination(@Nullable BlockPos pos);
}
