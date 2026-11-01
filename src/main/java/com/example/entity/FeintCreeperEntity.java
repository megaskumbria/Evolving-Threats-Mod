package com.example.entity;

/** Feint creeper: looks normal from afar, bursts into a rush up close. No custom name. */
public interface FeintCreeperEntity {

	boolean evolvingthreats$isFeintCreeper();

	void evolvingthreats$setFeintCreeper(boolean feint);

	/** At what distance (blocks) this creeper drops the disguise. 0 — not chosen yet. */
	double evolvingthreats$getTriggerDistance();

	void evolvingthreats$setTriggerDistance(double blocks);

	/** Disguise already dropped: stays fast even if the player runs away. */
	boolean evolvingthreats$isFeintRevealed();

	void evolvingthreats$setFeintRevealed(boolean revealed);
}
