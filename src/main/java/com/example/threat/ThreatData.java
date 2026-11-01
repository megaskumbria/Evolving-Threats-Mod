package com.example.threat;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted threat-scale state: the world time when day counting began.
 * Stored under the world folder (data/evolving-threats/threat.dat). No tunables here.
 *
 * <p>Set once when the mod first loads this world. Time is "world clock" ticks:
 * the global counter that does not reset each day (24000 ticks = 1 day).
 */
public final class ThreatData extends SavedData {

	/** Whether the start time was set. false — mod is seeing this world for the first time. */
	public boolean initialized;

	/** World clock at threat-scale start. May be negative (after day set command). */
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

	/** One copy per world in the overworld save. Any server dimension level works. */
	public static ThreatData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
