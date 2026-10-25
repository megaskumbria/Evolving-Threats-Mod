package com.example.entity;

/**
 * Зомби с динамитом в руке. Реализация — mixin на Zombie.
 */
public interface TntZombieEntity {

	boolean evolvingthreats$isTntZombie();

	void evolvingthreats$setTntZombie(boolean tntZombie);

	/** Тики до следующего динамита. 0 = можно бросать. */
	int evolvingthreats$getTntCooldown();

	void evolvingthreats$setTntCooldown(int ticks);
}
