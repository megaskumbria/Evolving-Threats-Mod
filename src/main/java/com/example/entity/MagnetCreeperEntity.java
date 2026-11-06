package com.example.entity;

/** Magnet creeper: pulls a noticed player in. No custom name. */
public interface MagnetCreeperEntity {

	boolean evolvingthreats$isMagnetCreeper();

	void evolvingthreats$setMagnetCreeper(boolean magnet);

	/** How long this creeper has been pulling its current target, in ticks. 0 — not pulling. */
	int evolvingthreats$getMagnetPullTicks();

	void evolvingthreats$setMagnetPullTicks(int ticks);

	/** Ticks since it last had a player target. Not saved: a reload starts this at 0. */
	int evolvingthreats$getMagnetLostTicks();

	void evolvingthreats$setMagnetLostTicks(int ticks);
}
