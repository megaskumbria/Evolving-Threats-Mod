package com.example.entity;

/** Какой шалкер выпал при появлении. Реализация — mixin на Shulker. */
public interface ShulkerVariant {

	boolean evolvingthreats$rolled();

	void evolvingthreats$setRolled(boolean rolled);

	/** Снаряды этого шалкера вешают замедление. */
	boolean evolvingthreats$slows();

	void evolvingthreats$setSlows(boolean slows);

	/** После обычного выстрела выпускает второй снаряд. */
	boolean evolvingthreats$doubleShot();

	void evolvingthreats$setDoubleShot(boolean doubleShot);

	/** Попадание телепортирует игрока. */
	boolean evolvingthreats$teleportsPlayer();

	void evolvingthreats$setTeleportsPlayer(boolean teleportsPlayer);

	/** Сколько тиков осталось до второго снаряда. 0 — второй не ждёт. */
	int evolvingthreats$secondShotTicks();

	void evolvingthreats$setSecondShotTicks(int ticks);

	/** Сколько тиков до пересадки на соседний блок. */
	int evolvingthreats$hopTicks();

	void evolvingthreats$setHopTicks(int ticks);
}
