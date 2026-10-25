package com.example.entity;

/** Фантом, который вешает слабость укусом. Реализация — mixin на Phantom. */
public interface WeakeningPhantomEntity {

	boolean evolvingthreats$weakensOnBite();

	void evolvingthreats$setWeakensOnBite(boolean weakens);
}
