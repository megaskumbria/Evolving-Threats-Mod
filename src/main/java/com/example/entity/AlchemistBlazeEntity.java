package com.example.entity;

/*Blaze alchemist. The implementation is mixin on Blaze. */
public interface AlchemistBlazeEntity {

	boolean evolvingthreats$isAlchemist();

	void evolvingthreats$setAlchemist(boolean alchemist);

	/*Ticks to the next ball that will cut the resistance. 0 = the next shot may cut. */
	int evolvingthreats$getAlchemistCooldown();

	void evolvingthreats$setAlchemistCooldown(int ticks);
}
