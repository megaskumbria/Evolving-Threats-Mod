package com.example.threat;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Что мир помнит о шкале угрозы: с какого момента она считает дни.
 * Лежит в папке мира (data/evolving-threats/threat.dat). Настроек здесь нет.
 *
 * <p>Момент ставится один раз, когда мод впервые загрузил этот мир. Время тут «часы мира»:
 * общий счётчик тиков, который не обнуляется каждый день (24000 тиков = 1 день).
 */
public final class ThreatData extends SavedData {

	/** Уже ли поставлен момент старта. false — мод видит этот мир впервые. */
	public boolean initialized;

	/** Часы мира в момент старта шкалы. Может быть отрицательным (после команды day set). */
	public long startClock;

	public static final Codec<ThreatData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(data -> data.initialized),
			Codec.LONG.optionalFieldOf("start_clock", 0L).forGetter(data -> data.startClock)
	).apply(instance, ThreatData::new));

	public static final SavedDataType<ThreatData> TYPE = new SavedDataType<>(
			EvolvingThreats.id("threat"),
			ThreatData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public ThreatData() {
	}

	private ThreatData(boolean initialized, long startClock) {
		this.initialized = initialized;
		this.startClock = startClock;
	}

	/** Данные хранятся один раз на весь мир, в верхнем мире. Можно передать любой мир сервера. */
	public static ThreatData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
