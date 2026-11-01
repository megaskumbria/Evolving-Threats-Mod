package com.example.entity;

/*A skeleton that changes the effect of an arrow. He can't hold an arrow. */
public interface RandomArrowSkeletonEntity {

	boolean evolvingthreats$isRandomArrow();

	void evolvingthreats$setRandomArrow(boolean randomArrow);

	/*The remaining effects of this circle, one letter at a time. Empty - the circle hasn't started yet. */
	String evolvingthreats$getArrowQueue();

	void evolvingthreats$setArrowQueue(String queue);

	/*The effect of last shot. I haven't fired yet. */
	String evolvingthreats$getLastArrow();

	void evolvingthreats$setLastArrow(String effect);
}
