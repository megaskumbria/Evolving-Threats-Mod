package com.example.entity;

/** Роль особого паука (ткач / матка). Реализация — {@link com.example.mixin.SpiderRoleMixin}. */
public interface SpiderRoleEntity {

	/** 0 = обычный паук, 1 = ткач, 2 = матка (см. {@link com.example.spider.SpiderThreats}). */
	int evolvingthreats$getSpiderRole();

	void evolvingthreats$setSpiderRole(int role);
}
