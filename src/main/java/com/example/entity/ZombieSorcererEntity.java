package com.example.entity;

/** Зомби-чародей. Реализация — mixin на Zombie. */
public interface ZombieSorcererEntity {

	boolean evolvingthreats$isSorcerer();

	void evolvingthreats$setSorcerer(boolean sorcerer);

	/** Тики до следующего приёма. 0 = можно колдовать. */
	int evolvingthreats$getSorcererCooldown();

	void evolvingthreats$setSorcererCooldown(int ticks);
}
