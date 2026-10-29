package com.example.entity;

/** Обычный зомби или скелет, который стал Бастионом. Реализация — mixin, не новый моб. */
public interface BastionEntity {

	boolean evolvingthreats$isBastion();

	void evolvingthreats$setBastion(boolean bastion);
}
