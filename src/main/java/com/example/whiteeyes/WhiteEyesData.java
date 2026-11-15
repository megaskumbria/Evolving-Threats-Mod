package com.example.whiteeyes;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which scale night already brought White Eyes, and which day already pushed a siege aside.
 * One world, stored with the overworld. Numbers to tune live in {@link WhiteEyes}.
 */
public final class WhiteEyesData extends SavedData {

	/** Scale day of the last night he actually appeared. -1 = never. */
	public int lastVisitDay = -1;

	/** Scale day we already moved a siege off. -1 = never. */
	public int yieldedForDay = -1;

	public static final Codec<WhiteEyesData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("last_visit_day", -1).forGetter(data -> data.lastVisitDay),
			Codec.INT.optionalFieldOf("yielded_for_day", -1).forGetter(data -> data.yieldedForDay)
	).apply(instance, WhiteEyesData::new));

	public static final SavedDataType<WhiteEyesData> TYPE = new SavedDataType<>(
			EvolvingThreats.id("white_eyes"),
			WhiteEyesData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public WhiteEyesData() {
	}

	public WhiteEyesData(int lastVisitDay, int yieldedForDay) {
		this.lastVisitDay = lastVisitDay;
		this.yieldedForDay = yieldedForDay;
	}

	public static WhiteEyesData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
