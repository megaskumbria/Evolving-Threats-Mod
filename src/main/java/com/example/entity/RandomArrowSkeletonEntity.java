package com.example.entity;

/** Скелет, который сам меняет эффект стрелы. Стрелу в руке не держит. */
public interface RandomArrowSkeletonEntity {

	boolean evolvingthreats$isRandomArrow();

	void evolvingthreats$setRandomArrow(boolean randomArrow);

	/** Оставшиеся эффекты этого круга, по одной букве. Пусто — круг ещё не начат. */
	String evolvingthreats$getArrowQueue();

	void evolvingthreats$setArrowQueue(String queue);

	/** Эффект прошлого выстрела. Пусто — ещё не стрелял. */
	String evolvingthreats$getLastArrow();

	void evolvingthreats$setLastArrow(String effect);
}
