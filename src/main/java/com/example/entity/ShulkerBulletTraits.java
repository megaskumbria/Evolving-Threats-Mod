package com.example.entity;

/** What does a particular shalker shell do? The implementation is mixin on ShulkerBullet. */
public interface ShulkerBulletTraits {

	boolean evolvingthreats$slowness();

	void evolvingthreats$setSlowness(boolean slowness);

	boolean evolvingthreats$longLevitation();

	void evolvingthreats$setLongLevitation(boolean longLevitation);

	boolean evolvingthreats$teleportsPlayer();

	void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer);
}
