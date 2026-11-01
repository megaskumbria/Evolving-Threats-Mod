package com.example.entity;

/*Creeper that throws dynamite and does not explode itself. The implementation is mixin on Creeper. */
public interface DynamiteCreeperEntity {

	boolean evolvingthreats$isDynamiteCreeper();

	void evolvingthreats$setDynamiteCreeper(boolean dynamite);
}
