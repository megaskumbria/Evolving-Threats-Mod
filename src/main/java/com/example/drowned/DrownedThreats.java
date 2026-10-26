package com.example.drowned;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Утопленник: черепаший шлем, лечение в воде и чары на трезубце, который уже выдала игра.
 * Шлем и чары решаются один раз при появлении. Лечение работает у всех, пока они в воде.
 * Новый трезубец мод не создаёт и удочку не забирает. Если игра успела повесить свои чары, мод их заменяет.
 */
public final class DrownedThreats {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — утопленник как в обычной игре. */
	public static final boolean ENABLED = true;

	/**
	 * Шанс (0.0–1.0) появиться в черепашьем шлеме.
	 * 0.25 = 25%, примерно каждый четвёртый. 0 = шлема нет. 1 = шлем у каждого.
	 * Если на голове уже что-то есть, шлем не надевается.
	 */
	public static final float HELMET_CHANCE = 0.25f;

	/**
	 * Уровень чар «Защита» на шлеме. Это и есть сопротивление урону.
	 * 2 = Защита II. 0 = шлем без чар, остаётся только своя броня панциря.
	 * 4 = максимум игры, Защита IV.
	 */
	public static final int HELMET_PROTECTION = 2;

	/**
	 * Шанс (0.0–1.0), что на трезубце, который уже выдала игра, будет «Громовержец».
	 * Считается только если трезубец уже в руке. Новый трезубец мод не создаёт.
	 * 0.30 = 30%. Остальные 70% получают одну другую чару: Пронзатель, Верность, Тягун или Прочность.
	 * 0 = «Громовержца» нет, всегда другая чара. 1 = «Громовержец» на каждом таком трезубце.
	 */
	public static final float CHANNELING_CHANCE = 0.30f;

	/**
	 * Шанс (0.0–1.0), что черепаший шлем выпадет при смерти.
	 * 0.085 = 8.5%, как обычная броня мобов. 0 = не выпадает. 1 = выпадает всегда.
	 * На трезубец не влияет: его выдала игра, и падает он по её правилам.
	 */
	public static final float DROP_CHANCE = 0.085f;

	/**
	 * Сколько здоровья возвращает лечение в воде. 2 = одно сердце.
	 * 0 = в воде не лечится.
	 */
	public static final float REGEN_HP = 2.0f;

	/**
	 * Как часто лечение, в секундах. 3 = раз в три секунды, пока утопленник в воде и не полный.
	 * На суше таймер стоит и начинается заново, когда он снова зайдёт в воду.
	 */
	public static final int REGEN_SECONDS = 3;

	// ========================================================================

	/** В одной секунде 20 тиков. */
	private static final int TICKS_PER_SECOND = 20;

	private DrownedThreats() {
	}

	/** Черепаший шлем и чары на уже выданный трезубец. Удочку не заменяет. */
	public static void onSpawn(Drowned drowned) {
		if (!ENABLED || drowned.level().isClientSide()) {
			return;
		}
		if (!(drowned.level() instanceof ServerLevel level)) {
			return;
		}
		if (drowned.getRandom().nextFloat() < HELMET_CHANCE && drowned.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			equipHelmet(level, drowned);
		}
		if (drowned.getMainHandItem().is(Items.TRIDENT)) {
			enchantTrident(level, drowned);
		}
	}

	/**
	 * Один тик. Возвращает, сколько тиков ещё ждать до лечения.
	 * На суше счётчик сбрасывается, лечение не идёт.
	 */
	public static int tickRegen(Drowned drowned, int ticksLeft) {
		int interval = Math.max(1, REGEN_SECONDS) * TICKS_PER_SECOND;
		if (!ENABLED || REGEN_HP <= 0.0f || REGEN_SECONDS <= 0 || drowned.level().isClientSide()) {
			return interval;
		}
		if (!drowned.isInWater() || !drowned.isAlive() || drowned.getHealth() >= drowned.getMaxHealth()) {
			return interval;
		}
		if (ticksLeft > 1) {
			return ticksLeft - 1;
		}
		drowned.heal(REGEN_HP);
		return interval;
	}

	/**
	 * Брошенный трезубец уже попал в цель и снял здоровье.
	 * Молния бьёт игрока, только если на трезубце «Громовержец» — его вешает мод, примерно на каждый третий такой трезубец.
	 * Тогда удар бьёт молнией даже без дождя и под крышей.
	 * В грозу под открытым небом молнию вызывает сама игра, второй раз не бьём.
	 */
	public static void onTridentHit(ThrownTrident trident, Entity target) {
		if (!ENABLED || !(target instanceof Player player) || !(trident.getOwner() instanceof Drowned)) {
			return;
		}
		if (!(trident.level() instanceof ServerLevel level) || !hasChanneling(level, trident.getWeaponItem())) {
			return;
		}
		boolean vanillaBolt = level.isThundering() && level.canSeeSky(player.blockPosition());
		if (vanillaBolt) {
			return;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(player.getX(), player.getY(), player.getZ());
		level.addFreshEntity(bolt);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0f, 1.0f);
	}

	private static void equipHelmet(ServerLevel level, Drowned drowned) {
		ItemStack helmet = new ItemStack(Items.TURTLE_HELMET);
		int protection = Math.min(4, HELMET_PROTECTION);
		if (protection > 0) {
			Holder<Enchantment> enchantment = enchantment(level, Enchantments.PROTECTION);
			EnchantmentHelper.updateEnchantments(helmet, mutable -> mutable.set(enchantment, protection));
		}
		drowned.setItemSlot(EquipmentSlot.HEAD, helmet);
		drowned.setDropChance(EquipmentSlot.HEAD, Math.max(0.0f, DROP_CHANCE));
	}

	/**
	 * Одна чара на трезубец игры. Свои чары игры, если они уже были, стираются.
	 * Уровень другой чары — от I до её максимума, каждый уровень равновероятен.
	 * «Громовержец» бывает только I.
	 */
	private static void enchantTrident(ServerLevel level, Drowned drowned) {
		ItemStack trident = drowned.getMainHandItem();
		if (!trident.is(Items.TRIDENT)) {
			return;
		}
		RandomSource random = drowned.getRandom();
		ResourceKey<Enchantment> key = random.nextFloat() < CHANNELING_CHANCE
				? Enchantments.CHANNELING
				: otherTridentEnchant(random.nextInt(4));
		Holder<Enchantment> holder = enchantment(level, key);
		int maxLevel = Math.max(1, holder.value().getMaxLevel());
		int levelValue = 1 + random.nextInt(maxLevel);
		EnchantmentHelper.updateEnchantments(trident, mutable -> {
			mutable.removeIf(ignored -> true);
			mutable.set(holder, levelValue);
		});
		drowned.setItemSlot(EquipmentSlot.MAINHAND, trident);
	}

	/** Пронзатель, Верность, Тягун, Прочность — поровну. «Громовержца» среди них нет. */
	private static ResourceKey<Enchantment> otherTridentEnchant(int roll) {
		return switch (roll) {
			case 0 -> Enchantments.IMPALING;
			case 1 -> Enchantments.LOYALTY;
			case 2 -> Enchantments.RIPTIDE;
			default -> Enchantments.UNBREAKING;
		};
	}

	private static boolean hasChanneling(ServerLevel level, ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment(level, Enchantments.CHANNELING), stack) > 0;
	}

	private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}
}
