package com.example.entity;

/** Крипер-обманка: издалека обычный, вблизи срывается в рывок. Ника нет. */
public interface FeintCreeperEntity {

	boolean evolvingthreats$isFeintCreeper();

	void evolvingthreats$setFeintCreeper(boolean feint);

	/** На каком расстоянии (блоки) этот крипер срывает маскировку. 0 — ещё не выбрал. */
	double evolvingthreats$getTriggerDistance();

	void evolvingthreats$setTriggerDistance(double blocks);

	/** Маскировка уже сорвана: дальше он быстрый, даже если игрок отбежал. */
	boolean evolvingthreats$isFeintRevealed();

	void evolvingthreats$setFeintRevealed(boolean revealed);
}
