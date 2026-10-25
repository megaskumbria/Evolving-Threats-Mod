package com.example.entity;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Участник отряда, который идёт в запомненную точку, а не за игроком.
 * Реализация — mixin на всех рейдеров (разбойник, поборник, разоритель).
 */
public interface IllagerPatrolMember {

	@Nullable
	BlockPos evolvingthreats$getPatrolDestination();

	void evolvingthreats$setPatrolDestination(@Nullable BlockPos pos);
}
