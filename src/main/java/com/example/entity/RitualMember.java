package com.example.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/*Participant in a ritual pack. The implementation is mixin on Zombie and Skeleton. */
public interface RitualMember {

	boolean evolvingthreats$isRitual();

	void evolvingthreats$setRitual(boolean ritual);

	/** 1 player, 2 crypers, 3 piglins, 4 dehumidifier. 0 is not a pack. */
	int evolvingthreats$getRitualHead();

	void evolvingthreats$setRitualHead(int head);

	@Nullable UUID evolvingthreats$getRitualGroup();

	void evolvingthreats$setRitualGroup(@Nullable UUID group);

	/*True is a red torch, false is a bone. */
	boolean evolvingthreats$isRitualTorch();

	void evolvingthreats$setRitualTorch(boolean torch);

	/*There was already cotton on the deathbed. So one mob doesn't explode twice. */
	boolean evolvingthreats$ritualExploded();

	void evolvingthreats$setRitualExploded(boolean exploded);
}
