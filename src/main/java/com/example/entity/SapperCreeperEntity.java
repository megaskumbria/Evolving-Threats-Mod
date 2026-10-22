package com.example.entity;

/** Крипер-подрывник: взрывает опору под игроком или стену, если дойти нельзя. */
public interface SapperCreeperEntity {

	boolean evolvingthreats$isSapperCreeper();

	void evolvingthreats$setSapperCreeper(boolean sapper);
}
