package com.example.entity;

/*Ordinary zombie or skeleton that became Bastion. The implementation is mixin, not a new mob. */
public interface BastionEntity {

	boolean evolvingthreats$isBastion();

	void evolvingthreats$setBastion(boolean bastion);
}
