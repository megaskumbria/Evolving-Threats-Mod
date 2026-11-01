package com.example.entity;

/** Data "carrier spawner on the head." Implementation - {@link com.example.mixin.MobSpawnerHeadDataMixin} */
public interface SpawnerHeadCarrierEntity {

	boolean evolvingthreats$isSpawnerHeadCarrier();

	void evolvingthreats$setSpawnerHeadCarrier(boolean carrier);

	/** How many hits from the last call (reset after each {@link com.example.spawnerhead.SpawnerHeadMob#HITS TO SUMMON}). */
	int evolvingthreats$getSpawnerHeadHitCount();

	void evolvingthreats$setSpawnerHeadHitCount(int count);

	/**
	 Called support of the carrier - never receives a spawner on the head during a spawn.
	 False = ordinary mob from the world.
	 */
	boolean evolvingthreats$isSpawnerReinforcement();

	void evolvingthreats$setSpawnerReinforcement(boolean reinforcement);
}
