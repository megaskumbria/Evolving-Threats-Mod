package com.example.entity;

/*Zombie wizard. The implementation is mixin on Zombie. */
public interface ZombieSorcererEntity {

	boolean evolvingthreats$isSorcerer();

	void evolvingthreats$setSorcerer(boolean sorcerer);

	/*Ticks until the next appointment. 0 = you can do magic. */
	int evolvingthreats$getSorcererCooldown();

	void evolvingthreats$setSorcererCooldown(int ticks);
}
