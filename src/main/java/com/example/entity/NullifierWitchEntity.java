package com.example.entity;

/** Ведьма-обнулитель. Реализация — mixin на Witch. */
public interface NullifierWitchEntity {

	boolean evolvingthreats$isNullifier();

	void evolvingthreats$setNullifier(boolean nullifier);

	/** Тики до следующего зелья, которое снимает эффекты. 0 = следующий бросок по игроку может снять. */
	int evolvingthreats$getNullifyCooldown();

	void evolvingthreats$setNullifyCooldown(int ticks);

	/**
	 * Ведьма уже решила, что текущий бросок — обнуляющее зелье.
	 * Флаг живёт только на время создания снаряда и в мир не пишется.
	 */
	boolean evolvingthreats$isNullifyArmed();

	void evolvingthreats$setNullifyArmed(boolean armed);
}
