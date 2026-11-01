package com.example.raid;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted natural raid-anchor state: which game day the last one spawned.
 * Used for spacing between anchors (see {@link RaidAnchorNatural#MIN_DAYS_BETWEEN}).
 * Stored under the world folder (data/evolving-threats/raid_anchor.dat). No tunables here.
 */
public final class RaidAnchorData extends SavedData {

	/** Game day when the last natural anchor spawned. -1 = never yet. */
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

	/** One copy per world in the overworld save. Any server dimension level works. */
	public static RaidAnchorData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
