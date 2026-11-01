package com.example.siege;

import com.example.EvolvingThreats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * What the game remembers about sieges between restarts: when the next one is and which phase we are in.
 * Stored in the world folder (data/evolving-threats/siege.dat). No balance tunables here:
 * change numbers and chat text in {@link SiegeNight} and {@link SiegeWaves}.
 *
 * Time here is in "world time": the global tick counter, not reset each day.
 * One game day = 24000 ticks.
 */
public final class SiegeData extends SavedData {

	/** No siege active; waiting for the scheduled day. */
	public static final int PHASE_IDLE = 0;

	/** Warning: night not started yet, but sleep and portals are already blocked. */
	public static final int PHASE_WARNING = 1;

	/** Active siege: mob waves are running. */
	public static final int PHASE_NIGHT = 2;

	/** Game day number when the next siege night happens. -1 = not scheduled yet. */
	public long nextSiegeDay = -1L;

	/** One of PHASE_*. */
	public int phase = PHASE_IDLE;

	/** World time when siege night begins. Used to know when waves start. */
	public long nightStartClock;

	/** World time when the siege ends. */
	public long endClock;

	/** Which siege already received the morning omen. Prevents repeating the hint. */
	public long omenSentFor = -1L;

	/**
	 * Current siege composition: mob type shares (sum 1.0), in {@link SiegeComposition.Kind} order.
	 * Empty — not chosen yet (chosen at siege start). Saved so after restart
	 * the siege continues with the same mob mix.
	 */
	public float[] shares = new float[0];

	/**
	 * Some player on this world has visited the Nether.
	 * While false, wither skeletons are excluded from random siege composition.
	 * Never resets to false. Debug force-single-type commands ignore this.
	 */
	public boolean netherVisited = false;

	public static final Codec<SiegeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("next_siege_day", -1L).forGetter(data -> data.nextSiegeDay),
			Codec.INT.optionalFieldOf("phase", PHASE_IDLE).forGetter(data -> data.phase),
			Codec.LONG.optionalFieldOf("night_start_clock", 0L).forGetter(data -> data.nightStartClock),
			Codec.LONG.optionalFieldOf("end_clock", 0L).forGetter(data -> data.endClock),
			Codec.LONG.optionalFieldOf("omen_sent_for", -1L).forGetter(data -> data.omenSentFor),
			Codec.FLOAT.listOf().optionalFieldOf("shares", List.of()).forGetter(data -> toList(data.shares)),
			Codec.BOOL.optionalFieldOf("nether_visited", false).forGetter(data -> data.netherVisited)
	).apply(instance, SiegeData::new));

	public static final SavedDataType<SiegeData> TYPE = new SavedDataType<>(
			EvolvingThreats.id("siege"),
			SiegeData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public SiegeData() {
	}

	private SiegeData(long nextSiegeDay, int phase, long nightStartClock, long endClock, long omenSentFor,
			List<Float> shares, boolean netherVisited) {
		this.nextSiegeDay = nextSiegeDay;
		this.phase = phase;
		this.nightStartClock = nightStartClock;
		this.endClock = endClock;
		this.omenSentFor = omenSentFor;
		this.netherVisited = netherVisited;
		this.shares = new float[shares.size()];
		for (int i = 0; i < this.shares.length; i++) {
			this.shares[i] = shares.get(i);
		}
	}

	private static List<Float> toList(float[] values) {
		List<Float> list = new ArrayList<>(values.length);
		for (float value : values) {
			list.add(value);
		}
		return list;
	}

	/** Saved once per world in the Overworld. Any server level can be passed. */
	public static SiegeData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
