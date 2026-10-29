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
 * Что игра помнит об осадах между перезапусками: когда следующая и на каком этапе мы сейчас.
 * Лежит в папке мира (data/evolving-threats/siege.dat). Здесь нет настроек для баланса:
 * числа и тексты меняются в {@link SiegeNight} и {@link SiegeWaves}.
 *
 * Время здесь везде в «часах мира»: это общий счётчик тиков, он не обнуляется каждый день.
 * Один игровой день = 24000 таких тиков.
 */
public final class SiegeData extends SavedData {

	/** Осады нет, ждём назначенный день. */
	public static final int PHASE_IDLE = 0;

	/** Предупреждение: ночь ещё не наступила, но спать и ходить в порталы уже нельзя. */
	public static final int PHASE_WARNING = 1;

	/** Сама осада: идут волны мобов. */
	public static final int PHASE_NIGHT = 2;

	/** Номер игрового дня, в ночь которого будет следующая осада. -1 = ещё не назначена. */
	public long nextSiegeDay = -1L;

	/** Один из PHASE_*. */
	public int phase = PHASE_IDLE;

	/** Когда начинается ночь осады (часы мира). Нужно, чтобы понять, когда волны стартуют. */
	public long nightStartClock;

	/** Когда осада заканчивается (часы мира). */
	public long endClock;

	/** Для какой осады уже показали утренний намёк. Чтобы он не повторялся. */
	public long omenSentFor = -1L;

	/**
	 * Состав текущей осады: доли видов мобов (в сумме 1.0), по порядку {@link SiegeComposition.Kind}.
	 * Пусто — состав ещё не выбран (выбирается при начале осады). Хранится, чтобы после перезапуска
	 * игры осада продолжилась с теми же мобами.
	 */
	public float[] shares = new float[0];

	public static final Codec<SiegeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("next_siege_day", -1L).forGetter(data -> data.nextSiegeDay),
			Codec.INT.optionalFieldOf("phase", PHASE_IDLE).forGetter(data -> data.phase),
			Codec.LONG.optionalFieldOf("night_start_clock", 0L).forGetter(data -> data.nightStartClock),
			Codec.LONG.optionalFieldOf("end_clock", 0L).forGetter(data -> data.endClock),
			Codec.LONG.optionalFieldOf("omen_sent_for", -1L).forGetter(data -> data.omenSentFor),
			Codec.FLOAT.listOf().optionalFieldOf("shares", List.of()).forGetter(data -> toList(data.shares))
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
			List<Float> shares) {
		this.nextSiegeDay = nextSiegeDay;
		this.phase = phase;
		this.nightStartClock = nightStartClock;
		this.endClock = endClock;
		this.omenSentFor = omenSentFor;
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

	/** Данные хранятся один раз на весь мир, в верхнем мире. Можно передать любой мир сервера. */
	public static SiegeData get(ServerLevel anyLevel) {
		return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
