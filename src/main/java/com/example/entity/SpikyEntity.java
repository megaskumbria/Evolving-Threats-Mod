package com.example.entity;

/** Обычный зомби или скелет, который стал Spiky. Реализация — mixin, не новый моб. */
public interface SpikyEntity {

	boolean evolvingthreats$isSpiky();

	void evolvingthreats$setSpiky(boolean spiky);
}
