package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 Captain of the skeletons or his assistant. Realization - mixin on a normal skeleton.
 Skeleton exhumerator and wanderer are not included here.
 */
public interface SkeletonSquadMember {

	/*0 = ordinary skeleton, 1 = captain, 2 = assistant. */
	int evolvingthreats$getSquadRole();

	void evolvingthreats$setSquadRole(int role);

	@Nullable
	UUID evolvingthreats$getSquadId();

	void evolvingthreats$setSquadId(@Nullable UUID id);

	List<UUID> evolvingthreats$getSquadMembers();

	void evolvingthreats$setSquadMembers(List<UUID> members);

	/*A player the captain follows himself. The assistant's empty. */
	@Nullable
	UUID evolvingthreats$getHuntTargetId();

	void evolvingthreats$setHuntTargetId(@Nullable UUID id);
}
