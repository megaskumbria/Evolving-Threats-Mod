package com.example.entity;

/** Скелет-снайпер: стреляет с дальней дистанции. Реализация — mixin на скелета. */
public interface SniperSkeletonEntity {

	boolean evolvingthreats$isSniper();

	void evolvingthreats$setSniper(boolean sniper);
}
