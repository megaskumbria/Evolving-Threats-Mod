package com.example.raid;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Что мир помнит о естественных Рейдовых якорях: в какой день появился последний.
 * Нужно для паузы между якорями (см. {@link RaidAnchorNatural#MIN_DAYS_BETWEEN}).
 * Лежит в папке мира (data/evolving-threats/raid_anchor.dat). Настроек здесь нет.
 */
public final class RaidAnchorData extends SavedData {

	/** Игровой день, когда появился последний естественный якорь. -1 = ещё ни разу. */
	public int lastNaturalAnchorDay = -1;

	public static final Codec<RaidAnchorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("last_natural_anchor_day", -1).forGetter(data -> data.lastNaturalAnchorDay)
	).apply(instance, RaidAnchorData::new));

	public static final SavedDataType<RaidAnchorData> TYPE = new SavedDataType<>(
			EvolvingThreats.id("raid_anchor"),
			RaidAnchorData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public RaidAnchorData() {
	}

	private RaidAnchorData(int lastNaturalAnchorDay) {
		this.lastNaturalAnchorDay = lastNaturalAnchorDay;
	}

	/** Данные хранятся один раз на весь мир, в верхнем мире. Можно передать любой мир сервера. */
	public static RaidAnchorData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
