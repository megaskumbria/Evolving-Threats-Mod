package com.example.threat;

import com.example.EvolvingThreats;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client packet: when the world started counting threat days.
 * Used only for the on-screen threat bar: the client computes day and moon locally
 * but needs the mod's start timestamp.
 * Sent on join and after {@code /evolvingthreats day set}.
 */
public record ThreatStartPayload(long startClock) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ThreatStartPayload> TYPE =
			new CustomPacketPayload.Type<>(EvolvingThreats.id("threat_start"));

	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ThreatStartPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.LONG, ThreatStartPayload::startClock, ThreatStartPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
