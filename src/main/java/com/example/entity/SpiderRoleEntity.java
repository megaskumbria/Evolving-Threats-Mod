package com.example.entity;

/*The role of a special spider (weaver / uterus). Implementation - {@link com.example.mixin.SpiderRoleMixin} */
public interface SpiderRoleEntity {

	/** 0 = common spider, 1 = weaver, 2 = uterus (see {@link com.example.spider.SpiderThreats}). */
	int evolvingthreats$getSpiderRole();

	void evolvingthreats$setSpiderRole(int role);
}
