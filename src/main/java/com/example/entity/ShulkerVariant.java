package com.example.entity;

/*What kind of shalker fell on the appearance. The implementation is mixin on Shulker. */
public interface ShulkerVariant {

	boolean evolvingthreats$rolled();

	void evolvingthreats$setRolled(boolean rolled);

	/*The shells of this shalker are decelerating. */
	boolean evolvingthreats$slows();

	void evolvingthreats$setSlows(boolean slows);

	/*After a regular shot, the second projectile is fired. */
	boolean evolvingthreats$doubleShot();

	void evolvingthreats$setDoubleShot(boolean doubleShot);

	/*The hit teleports the player. */
	boolean evolvingthreats$teleportsPlayer();

	void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer);

	/*How many tics are left before the second projectile. 0 doesn't wait for second. */
	int evolvingthreats$secondShotTicks();

	void evolvingthreats$setSecondShotTicks(int ticks);

	/*How many tics before transferring to a neighboring block? */
	int evolvingthreats$hopTicks();

	void evolvingthreats$setHopTicks(int ticks);
}
