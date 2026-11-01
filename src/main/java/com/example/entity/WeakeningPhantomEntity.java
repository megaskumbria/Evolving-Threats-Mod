package com.example.entity;

/*A phantom that hangs weakness with a bite. The implementation is mixin on Phantom. */
public interface WeakeningPhantomEntity {

	boolean evolvingthreats$weakensOnBite();

	void evolvingthreats$setWeakensOnBite(boolean weakens);
}
