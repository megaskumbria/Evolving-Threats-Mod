package com.example.witch;

import com.example.entity.NullifierWitchEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Бросок ведьмы в игрока.
 * Часть бутылок — новые эффекты. Часть обычных и новых остаётся в воздухе туманом.
 * Лечение союзника в рейде и белое зелье обнулителя сюда не попадают.
 * Пока ведьма пьёт сама, броска нет: игра его и так не делает.
 */
public final class WitchThrownPotions {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — ведьма снова кидает только яд, замедление, слабость и мгновенный урон. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0), что бросок в игрока будет из нового списка, а не обычным.
	 * 0.50 = 50%, примерно каждый второй. 0 = новых эффектов нет. 1 = обычные в игрока не летят.
	 */
	public static final float SPECIAL_CHANCE = 0.50f;

	/**
	 * Шанс (0.0–1.0), что бутылка туманная: на месте попадания остаётся облако.
	 * 0.35 = 35%. Касается и новых эффектов, и обычных яда, замедления, слабости и урона.
	 * 0 = только взрывные брызги. 1 = каждый бросок в игрока оставляет туман.
	 * Белое зелье обнулителя всегда взрывное: туман эффекты не снимает.
	 */
	public static final float LINGERING_CHANCE = 0.35f;

	/**
	 * Замедление IV, секунды. Случайное число от минимума до максимума включительно.
	 * 15 и 20 = от 15 до 20 секунд. IV почти не даёт идти.
	 * 0 в максимуме выключает это зелье. Сила: 3 = IV, 0 было бы обычное замедление.
	 */
	public static final int SLOWNESS_MIN_SECONDS = 15;
	public static final int SLOWNESS_MAX_SECONDS = 20;
	public static final int SLOWNESS_AMPLIFIER = 3;

	/** Слепота, секунды. 5 и 10 = от 5 до 10. Экран темнеет, ведьму плохо видно. */
	public static final int BLINDNESS_MIN_SECONDS = 5;
	public static final int BLINDNESS_MAX_SECONDS = 10;

	/**
	 * Иссушение I, секунды. 10 = десять секунд.
	 * Снимает 1 здоровье примерно раз в 2 секунды, за 10 секунд это около 2 сердец чёрного урона.
	 * 1 = Иссушение II, тикает вдвое чаще. 0 = иссушения нет.
	 */
	public static final int WITHER_SECONDS = 10;
	public static final int WITHER_AMPLIFIER = 0;

	/**
	 * Голод I, секунды. 20 и 30 = от 20 до 30.
	 * За бой заметно сажает сытость. Не минуты: ведьма кидает часто, длинный голод не отпускал бы.
	 */
	public static final int HUNGER_MIN_SECONDS = 20;
	public static final int HUNGER_MAX_SECONDS = 30;

	/** Тошнота, секунды. 8 и 12 = экран качает недолго. Урона почти нет. */
	public static final int NAUSEA_MIN_SECONDS = 8;
	public static final int NAUSEA_MAX_SECONDS = 12;

	/**
	 * Усталость I, секунды. 15 и 20 = копать примерно втрое медленнее.
	 * 1 в силе = Усталость II, блоки почти не ломаются. 2 = как луч древнего стража.
	 */
	public static final int FATIGUE_MIN_SECONDS = 15;
	public static final int FATIGUE_MAX_SECONDS = 20;
	public static final int FATIGUE_AMPLIFIER = 0;

	/**
	 * Левитация, секунды. 4 и 6 = короткий подъём и падение.
	 * 8 секунд поднимают примерно на 8 блоков, как долгая стрела скелета. Здесь короче.
	 */
	public static final int LEVITATION_MIN_SECONDS = 4;
	public static final int LEVITATION_MAX_SECONDS = 6;

	/**
	 * Во сколько раз короче эффект у туманной бутылки с обычным зельем (яд, замедление, слабость).
	 * 0.25 = четверть, как у туманного зелья в обычной игре.
	 * Новые эффекты этот множитель не режет: у них секунды сверху.
	 */
	public static final float VANILLA_LINGERING_SCALE = 0.25f;

	// ========================================================================

	private WitchThrownPotions() {
	}

	/**
	 * Подмена бутылки в момент броска.
	 * Если менять нечего, уходит та, которую ведьма уже выбрала.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Projectile launch(
			Projectile.ProjectileFactory creator,
			ServerLevel level,
			ItemStack stack,
			LivingEntity source,
			double x,
			double y,
			double z,
			float power,
			float uncertainty) {
		Choice choice = choose(source, stack);
		if (choice == null) {
			return Projectile.spawnProjectileUsingShoot(creator, level, stack, source, x, y, z, power, uncertainty);
		}
		Projectile.ProjectileFactory factory = choice.lingering()
				? (Projectile.ProjectileFactory) ThrownLingeringPotion::new
				: ThrownSplashPotion::new;
		return Projectile.spawnProjectileUsingShoot(factory, level, choice.stack(), source, x, y, z, power, uncertainty);
	}

	private static Choice choose(LivingEntity source, ItemStack vanillaStack) {
		if (!ENABLED || !(source instanceof Witch witch) || !(witch.getTarget() instanceof Player)) {
			return null;
		}
		// Белая бутылка уже назначена. Её подменит свой код, туман ей не нужен.
		if (witch instanceof NullifierWitchEntity data && data.evolvingthreats$isNullifyArmed()) {
			return null;
		}
		RandomSource random = witch.getRandom();
		boolean lingering = LINGERING_CHANCE > 0.0f && random.nextFloat() < LINGERING_CHANCE;
		boolean special = SPECIAL_CHANCE > 0.0f && random.nextFloat() < SPECIAL_CHANCE;
		if (special) {
			ItemStack stack = specialStack(random, lingering);
			if (stack != null) {
				return new Choice(stack, lingering);
			}
		}
		if (!lingering) {
			return null;
		}
		return new Choice(lingeringVanilla(vanillaStack), true);
	}

	private static ItemStack specialStack(RandomSource random, boolean lingering) {
		List<Brew> brews = brews();
		if (brews.isEmpty()) {
			return null;
		}
		Brew brew = brews.get(random.nextInt(brews.size()));
		int seconds = rollSeconds(random, brew.minSeconds(), brew.maxSeconds());
		ItemStack stack = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
				Optional.empty(),
				Optional.of(brew.effect().value().getColor()),
				List.of(new MobEffectInstance(
						brew.effect(),
						Math.max(1, seconds) * 20,
						Math.max(0, brew.amplifier()),
						false,
						true,
						true)),
				Optional.empty()));
		return stack;
	}

	/** Обычные яд, замедление, слабость и урон, но бутылка туманная. */
	private static ItemStack lingeringVanilla(ItemStack splash) {
		ItemStack stack = new ItemStack(Items.LINGERING_POTION);
		PotionContents contents = splash.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			stack.set(DataComponents.POTION_CONTENTS, contents);
		}
		stack.set(DataComponents.POTION_DURATION_SCALE, VANILLA_LINGERING_SCALE);
		return stack;
	}

	private static List<Brew> brews() {
		List<Brew> list = new ArrayList<>();
		add(list, MobEffects.SLOWNESS, SLOWNESS_MIN_SECONDS, SLOWNESS_MAX_SECONDS, SLOWNESS_AMPLIFIER);
		add(list, MobEffects.BLINDNESS, BLINDNESS_MIN_SECONDS, BLINDNESS_MAX_SECONDS, 0);
		add(list, MobEffects.WITHER, WITHER_SECONDS, WITHER_SECONDS, WITHER_AMPLIFIER);
		add(list, MobEffects.HUNGER, HUNGER_MIN_SECONDS, HUNGER_MAX_SECONDS, 0);
		add(list, MobEffects.NAUSEA, NAUSEA_MIN_SECONDS, NAUSEA_MAX_SECONDS, 0);
		add(list, MobEffects.MINING_FATIGUE, FATIGUE_MIN_SECONDS, FATIGUE_MAX_SECONDS, FATIGUE_AMPLIFIER);
		add(list, MobEffects.LEVITATION, LEVITATION_MIN_SECONDS, LEVITATION_MAX_SECONDS, 0);
		return list;
	}

	/** Максимум 0 или меньше — этого зелья в списке нет. */
	private static void add(List<Brew> list, Holder<MobEffect> effect, int minSeconds, int maxSeconds, int amplifier) {
		if (Math.max(minSeconds, maxSeconds) <= 0) {
			return;
		}
		list.add(new Brew(effect, minSeconds, maxSeconds, amplifier));
	}

	private static int rollSeconds(RandomSource random, int minSeconds, int maxSeconds) {
		int low = Math.min(minSeconds, maxSeconds);
		int high = Math.max(minSeconds, maxSeconds);
		if (high <= low) {
			return Math.max(1, low);
		}
		return low + random.nextInt(high - low + 1);
	}

	private record Brew(Holder<MobEffect> effect, int minSeconds, int maxSeconds, int amplifier) {
	}

	private record Choice(ItemStack stack, boolean lingering) {
	}
}
