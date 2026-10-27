package com.example.threat;

import com.example.EvolvingThreats;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Пакет «сервер → игрок»: с какого момента мир считает дни угрозы. Нужен только для шкалы на экране:
 * клиент сам считает день и луну, но ему надо знать, когда мод начал отсчёт.
 * Шлётся при входе в мир и при команде /evolvingthreats day set.
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
