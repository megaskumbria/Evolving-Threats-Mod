package com.example.entity;

/*The Zero Witch. This is a mixin on Witch. */
public interface NullifierWitchEntity {

	boolean evolvingthreats$isNullifier();

	void evolvingthreats$setNullifier(boolean nullifier);

	/*Tics until the next potion, which relieves the effects. 0 = the next throw on the player can take off. */
	int evolvingthreats$getNullifyCooldown();

	void evolvingthreats$setNullifyCooldown(int ticks);

	/**
	 The witch has already decided that the current throw is a nullifying potion.
	 The flag lives only during the creation of the projectile and is not written into the world.
	 */
	boolean evolvingthreats$isNullifyArmed();

	void evolvingthreats$setNullifyArmed(boolean armed);
}
