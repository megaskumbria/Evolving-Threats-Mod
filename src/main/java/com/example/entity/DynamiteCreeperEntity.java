package com.example.entity;

/** Крипер, который кидает динамит и не взрывается сам. Реализация — mixin на Creeper. */
public interface DynamiteCreeperEntity {

	boolean evolvingthreats$isDynamiteCreeper();

	void evolvingthreats$setDynamiteCreeper(boolean dynamite);
}
