package com.example.entity;

/** Крипер, чей взрыв уничтожает щит, если игрок им закрылся. */
public interface ShieldBreakCreeperEntity {

	boolean evolvingthreats$isShieldBreakCreeper();

	void evolvingthreats$setShieldBreakCreeper(boolean shieldBreak);
}
