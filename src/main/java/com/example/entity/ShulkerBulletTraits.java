package com.example.entity;

/** Что делает конкретный снаряд шалкера. Реализация — mixin на ShulkerBullet. */
public interface ShulkerBulletTraits {

	boolean evolvingthreats$slowness();

	void evolvingthreats$setSlowness(boolean slowness);

	boolean evolvingthreats$longLevitation();

	void evolvingthreats$setLongLevitation(boolean longLevitation);

	boolean evolvingthreats$teleportsPlayer();

	void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer);
}
