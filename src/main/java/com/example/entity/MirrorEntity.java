package com.example.entity;

/** Моб, чей ближний удар игрока возвращается тому же игроку. */
public interface MirrorEntity {

	boolean evolvingthreats$isMirror();

	void evolvingthreats$setMirror(boolean mirror);
}
