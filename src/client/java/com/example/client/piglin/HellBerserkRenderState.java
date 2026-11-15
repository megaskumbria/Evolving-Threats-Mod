package com.example.client.piglin;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/**
 * Armed render state plus whether he is in the brute's attack stance.
 * While that is true the right arm holds the axe up, like a piglin brute.
 */
public class HellBerserkRenderState extends ArmedEntityRenderState {
	public boolean aggressive;
}
