package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Капитан скелетов или его помощник. Реализация — mixin на обычного скелета.
 * Скелет-иссушитель и странник сюда не входят.
 */
public interface SkeletonSquadMember {

	/** 0 = обычный скелет, 1 = капитан, 2 = помощник. */
	int evolvingthreats$getSquadRole();

	void evolvingthreats$setSquadRole(int role);

	@Nullable
	UUID evolvingthreats$getSquadId();

	void evolvingthreats$setSquadId(@Nullable UUID id);

	List<UUID> evolvingthreats$getSquadMembers();

	void evolvingthreats$setSquadMembers(List<UUID> members);

	/** Игрок, за которым капитан идёт сам. У помощника пусто. */
	@Nullable
	UUID evolvingthreats$getHuntTargetId();

	void evolvingthreats$setHuntTargetId(@Nullable UUID id);
}
