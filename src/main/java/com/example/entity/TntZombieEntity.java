package com.example.entity;

/**
 Zombies with dynamite in hand. The implementation is mixin on Zombie.
 */
public interface TntZombieEntity {

	boolean evolvingthreats$isTntZombie();

	void evolvingthreats$setTntZombie(boolean tntZombie);

	/*Ticks until the next dynamite. 0 = you can throw. */
	int evolvingthreats$getTntCooldown();

	void evolvingthreats$setTntCooldown(int ticks);
}
