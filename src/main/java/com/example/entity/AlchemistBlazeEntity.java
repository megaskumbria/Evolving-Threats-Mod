package com.example.entity;

/** Блейз-алхимик. Реализация — mixin на Blaze. */
public interface AlchemistBlazeEntity {

	boolean evolvingthreats$isAlchemist();

	void evolvingthreats$setAlchemist(boolean alchemist);

	/** Тики до следующего шара, который срежет стойкость. 0 = следующий выстрел может срезать. */
	int evolvingthreats$getAlchemistCooldown();

	void evolvingthreats$setAlchemistCooldown(int ticks);
}
