package com.example.entity;

/**
 * A creeper that appeared already charged.
 * Lightning sets the same powered look, but does not set this flag.
 */
public interface BornChargedCreeperEntity {

	boolean evolvingthreats$wasBornCharged();

	void evolvingthreats$setBornCharged(boolean bornCharged);
}
