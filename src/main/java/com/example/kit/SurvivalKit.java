package com.example.kit;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * Тестовый набор в инвентарь. Не механика мобов: только команда.
 * <p>
 * В чат: {@code /etkit} или {@code /evolvingthreats give kit}.
 * Нужны читы, как у остальных команд мода.
 * Вещи не надеваются сами. Если инвентарь полон, лишнее падает под ноги.
 * <p>
 * Чары — максимум наковальни в выживании, и только те, что стоят на одной вещи вместе.
 * Защита от взрыва, от огня и от снарядов сюда не входят: с обычной Защитой они не сочетаются.
 * Проклятия не ставятся.
 */
public final class SurvivalKit {

	// ===================== SETTINGS (меняй эти числа) =====================

	/** Сколько обычных золотых яблок. Не зачарованных. 64 = полный стак. 0 = не выдавать. */
	public static final int GOLDEN_APPLE_COUNT = 64;

	/** Сколько тотемов бессмертия. 2 — как просили. 0 = не выдавать. */
	public static final int TOTEM_COUNT = 2;

	/**
	 * Сколько ракет для элитр. 64 = полный стак.
	 * Без звезды: толкают в полёте и не взрываются в лицо. 0 = не выдавать.
	 */
	public static final int FIREWORK_COUNT = 64;

	/**
	 * Длительность полёта ракеты. В выживании 1–3 (сколько пороха в крафте).
	 * 3 = максимальный толчок элитр.
	 */
	public static final int FIREWORK_FLIGHT = 3;

	/**
	 * Стрелы к луку. Бесконечность их не тратит, но хотя бы одна должна лежать в инвентаре.
	 * 64 = полный стак. 0 = не выдавать.
	 */
	public static final int ARROW_COUNT = 64;

	/** Защита на каждом куске брони. Потолок выживания — IV. */
	public static final int PROTECTION = 4;
	/** Шипы. Потолок — III. 0 = убрать шипы (броня не колется в ответ и медленнее ломается). */
	public static final int THORNS = 3;
	/** Прочность. Потолок — III. */
	public static final int UNBREAKING = 3;
	/** Починка. 1 = есть, 0 = нет. Чинится опытом, на наковальне с книгой. */
	public static final int MENDING = 1;

	/** Подводное дыхание, только шлем. Потолок — III. */
	public static final int RESPIRATION = 3;
	/** Подводник, только шлем. 1 = копать под водой как на суше. */
	public static final int AQUA_AFFINITY = 1;
	/** Проворство, только поножи. Потолок — III. Книга из древнего города. */
	public static final int SWIFT_SNEAK = 3;
	/** Невесомость, только ботинки. Потолок — IV. */
	public static final int FEATHER_FALLING = 4;
	/**
	 * Подводная ходьба, только ботинки. Потолок — III.
	 * С ледоходом вместе не работает. Сейчас включена ходьба, ледоход выключен (см. FROST_WALKER).
	 * Чтобы был ледоход: поставь здесь 0, а FROST_WALKER = 2.
	 */
	public static final int DEPTH_STRIDER = 3;
	/** Ледоход, только ботинки. Потолок — II. 0 = выключен, пока включена подводная ходьба. */
	public static final int FROST_WALKER = 0;
	/** Скорость души, только ботинки. Потолок — III. С подводной ходьбой сочетается. */
	public static final int SOUL_SPEED = 3;

	/** Острота меча. Потолок — V. Небесная кара на мече не стоит: она на топоре. Вместе не работают. */
	public static final int SWORD_SHARPNESS = 5;
	/** Отбрасывание меча. Потолок — II. 0 = убрать: мобов не будет откидывать. */
	public static final int SWORD_KNOCKBACK = 2;
	/** Заговор огня. Потолок — II. */
	public static final int SWORD_FIRE_ASPECT = 2;
	/** Добыча. Потолок — III. */
	public static final int SWORD_LOOTING = 3;
	/** Разящий клинок. Потолок — III. Только меч, в Java. */
	public static final int SWORD_SWEEPING = 3;

	/** Сила лука. Потолок — V. */
	public static final int BOW_POWER = 5;
	/** Откидывание лука. Потолок — II. */
	public static final int BOW_PUNCH = 2;
	/** Воспламенение. 1 = стрела поджигает. */
	public static final int BOW_FLAME = 1;
	/**
	 * Бесконечность на луке. 1 = есть, Починки на этом луке нет.
	 * В выживании они вместе не ставятся.
	 * 0 = убрать Бесконечность и вернуть Починку, как на остальном наборе.
	 */
	public static final int BOW_INFINITY = 1;

	/** Эффективность кирки, топора и лопаты. Потолок — V. */
	public static final int TOOL_EFFICIENCY = 5;
	/**
	 * Удача на кирке, топоре и лопате. Потолок — III.
	 * Шёлковое касание сюда не ставится: с удачей на одной вещи не работает.
	 */
	public static final int TOOL_FORTUNE = 3;
	/** Небесная кара топора. Потолок — V. Острота на этом топоре не стоит. */
	public static final int AXE_SMITE = 5;

	// ========================================================================

	private SurvivalKit() {
	}

	/** Кладёт набор игроку, который ввёл команду. С консоли не работает. */
	public static int give(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Команду нужно вводить самому, не из консоли"));
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Команда только на сервере"));
			return 0;
		}

		give(player, armor(level, Items.NETHERITE_HELMET,
				enchant(Enchantments.RESPIRATION, RESPIRATION),
				enchant(Enchantments.AQUA_AFFINITY, AQUA_AFFINITY)));
		give(player, armor(level, Items.NETHERITE_CHESTPLATE));
		give(player, armor(level, Items.NETHERITE_LEGGINGS,
				enchant(Enchantments.SWIFT_SNEAK, SWIFT_SNEAK)));
		give(player, armor(level, Items.NETHERITE_BOOTS,
				enchant(Enchantments.FEATHER_FALLING, FEATHER_FALLING),
				enchant(Enchantments.DEPTH_STRIDER, DEPTH_STRIDER),
				enchant(Enchantments.FROST_WALKER, FROST_WALKER),
				enchant(Enchantments.SOUL_SPEED, SOUL_SPEED)));
		give(player, tool(level, Items.ELYTRA));

		give(player, tool(level, Items.NETHERITE_SWORD,
				enchant(Enchantments.SHARPNESS, SWORD_SHARPNESS),
				enchant(Enchantments.KNOCKBACK, SWORD_KNOCKBACK),
				enchant(Enchantments.FIRE_ASPECT, SWORD_FIRE_ASPECT),
				enchant(Enchantments.LOOTING, SWORD_LOOTING),
				enchant(Enchantments.SWEEPING_EDGE, SWORD_SWEEPING)));
		give(player, bow(level));
		give(player, tool(level, Items.NETHERITE_PICKAXE,
				enchant(Enchantments.EFFICIENCY, TOOL_EFFICIENCY),
				enchant(Enchantments.FORTUNE, TOOL_FORTUNE)));
		give(player, tool(level, Items.NETHERITE_AXE,
				enchant(Enchantments.SMITE, AXE_SMITE),
				enchant(Enchantments.EFFICIENCY, TOOL_EFFICIENCY),
				enchant(Enchantments.FORTUNE, TOOL_FORTUNE)));
		give(player, tool(level, Items.NETHERITE_SHOVEL,
				enchant(Enchantments.EFFICIENCY, TOOL_EFFICIENCY),
				enchant(Enchantments.FORTUNE, TOOL_FORTUNE)));

		give(player, plain(Items.TOTEM_OF_UNDYING, TOTEM_COUNT));
		give(player, plain(Items.GOLDEN_APPLE, GOLDEN_APPLE_COUNT));
		give(player, plain(Items.ARROW, ARROW_COUNT));
		give(player, fireworks());

		source.sendSuccess(() -> Component.literal("Набор для выживания положен в инвентарь"), true);
		return 1;
	}

	/** Защита, шипы, прочность и починка — на каждом куске брони. Плюс чары этого слота. */
	private static ItemStack armor(ServerLevel level, Item item, Level... extra) {
		Level[] all = new Level[4 + extra.length];
		all[0] = enchant(Enchantments.PROTECTION, PROTECTION);
		all[1] = enchant(Enchantments.THORNS, THORNS);
		all[2] = enchant(Enchantments.UNBREAKING, UNBREAKING);
		all[3] = enchant(Enchantments.MENDING, MENDING);
		System.arraycopy(extra, 0, all, 4, extra.length);
		return enchanted(level, item, 1, all);
	}

	/** Прочность и починка есть у меча, инструментов и элитр. Лук собирается отдельно. */
	private static ItemStack tool(ServerLevel level, Item item, Level... extra) {
		Level[] all = new Level[2 + extra.length];
		all[0] = enchant(Enchantments.UNBREAKING, UNBREAKING);
		all[1] = enchant(Enchantments.MENDING, MENDING);
		System.arraycopy(extra, 0, all, 2, extra.length);
		return enchanted(level, item, 1, all);
	}

	/**
	 * Лук с Бесконечностью не получает Починку.
	 * Если BOW_INFINITY = 0, вместо неё ставится Починка.
	 */
	private static ItemStack bow(ServerLevel level) {
		boolean infinity = BOW_INFINITY > 0;
		return enchanted(level, Items.BOW, 1,
				enchant(Enchantments.UNBREAKING, UNBREAKING),
				enchant(Enchantments.MENDING, infinity ? 0 : MENDING),
				enchant(Enchantments.POWER, BOW_POWER),
				enchant(Enchantments.PUNCH, BOW_PUNCH),
				enchant(Enchantments.FLAME, BOW_FLAME),
				enchant(Enchantments.INFINITY, BOW_INFINITY));
	}

	private static ItemStack enchanted(ServerLevel level, Item item, int count, Level... enchantments) {
		ItemStack stack = new ItemStack(item, count);
		var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		EnchantmentHelper.updateEnchantments(stack, mutable -> {
			for (Level enchantment : enchantments) {
				// 0 в константе выше = этого чара на вещи нет.
				if (enchantment.level <= 0) {
					continue;
				}
				Holder<Enchantment> holder = registry.getOrThrow(enchantment.key);
				mutable.set(holder, enchantment.level);
			}
		});
		return stack;
	}

	private static ItemStack plain(Item item, int count) {
		if (count <= 0) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item, count);
	}

	/** Ракета без звезды: только порох и бумага, для элитр. */
	private static ItemStack fireworks() {
		if (FIREWORK_COUNT <= 0) {
			return ItemStack.EMPTY;
		}
		int flight = Math.clamp(FIREWORK_FLIGHT, 1, 3);
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, FIREWORK_COUNT);
		stack.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of()));
		return stack;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack)) {
			// false — не бросок из руки, вещь падает под ноги.
			// SERVER_ONLY — выброс решает сервер (команда), клиент его не предугадывал.
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static Level enchant(ResourceKey<Enchantment> key, int level) {
		return new Level(key, level);
	}

	private record Level(ResourceKey<Enchantment> key, int level) {
	}
}
