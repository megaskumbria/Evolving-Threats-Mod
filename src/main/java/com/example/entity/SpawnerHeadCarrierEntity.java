package com.example.entity;

/** Данные «носителя спавнера на голове». Реализация — {@link com.example.mixin.MobSpawnerHeadDataMixin}. */
public interface SpawnerHeadCarrierEntity {

	boolean evolvingthreats$isSpawnerHeadCarrier();

	void evolvingthreats$setSpawnerHeadCarrier(boolean carrier);

	/** Сколько ударов игрока с последнего призыва (сбрасывается после каждых {@link com.example.spawnerhead.SpawnerHeadMob#HITS_TO_SUMMON}). */
	int evolvingthreats$getSpawnerHeadHitCount();

	void evolvingthreats$setSpawnerHeadHitCount(int count);

	/**
	 * Призванная подмога носителя — никогда не получает спавнер на голову при спавне.
	 * false = обычный моб из мира.
	 */
	boolean evolvingthreats$isSpawnerReinforcement();

	void evolvingthreats$setSpawnerReinforcement(boolean reinforcement);
}
