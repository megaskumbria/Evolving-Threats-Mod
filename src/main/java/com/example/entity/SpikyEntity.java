package com.example.entity;

/*Ordinary zombie or skeleton that became Spiky. The implementation is mixin, not a new mob. */
public interface SpikyEntity {

	boolean evolvingthreats$isSpiky();

	void evolvingthreats$setSpiky(boolean spiky);
}
