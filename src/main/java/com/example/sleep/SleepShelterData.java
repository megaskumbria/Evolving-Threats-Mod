package com.example.sleep;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which night each player already failed to sleep through.
 * Stored in the world folder. A failed attempt stays failed until the next day,
 * including after a relog, so the same night cannot be rolled again.
 */
public final class SleepShelterData extends SavedData {

	private final Map<UUID, Long> failedDay = new HashMap<>();

	public static final Codec<SleepShelterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
					.optionalFieldOf("failed_day", Map.of())
					.forGetter(data -> Map.copyOf(data.failedDay))
	).apply(instance, SleepShelterData::new));

	public static final SavedDataType<SleepShelterData> TYPE = new SavedDataType<>(
			EvolvingThreats.id("sleep_shelter"),
			SleepShelterData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public SleepShelterData() {
	}

	private SleepShelterData(Map<UUID, Long> failedDay) {
		this.failedDay.putAll(failedDay);
	}

	public static SleepShelterData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean locked(UUID player, long dayIndex) {
		Long failed = failedDay.get(player);
		return failed != null && failed == dayIndex;
	}

	/** Remember this day, and drop nights that have already ended. */
	public void lock(UUID player, long dayIndex) {
		failedDay.entrySet().removeIf(entry -> entry.getValue() < dayIndex);
		failedDay.put(player, dayIndex);
		setDirty();
	}
}
