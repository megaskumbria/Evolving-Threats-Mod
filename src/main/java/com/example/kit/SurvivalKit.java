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
 * Test kit for inventory. Not mob mechanics: just a team.
 * <p>
 * In chat: {@code /etkit} or {@code /evolvingthreats give kit}.
 You need cheats like the other fashion teams.
 * Things don't wear themselves. If the inventory is full, the excess falls under your feet.
 * <p>
 Charms are the maximum anvil in survival, and only those that stand on one thing together.
 Explosion, fire and shell protection are not included here: they are not combined with conventional Protection.
 There's no curse.
 */
public final class SurvivalKit {

	// ===================== SETTINGS (change these numbers) =====================

	/*How many ordinary golden apples? Not enchanted. 64 = full stack. 0 = not to give. */
	public static final int GOLDEN_APPLE_COUNT = 64;

	/*How many totems of immortality. 2 - as requested. 0 = not to give. */
	public static final int TOTEM_COUNT = 2;

	/**
	 How many missiles for the elite. 64 = full stack.
	 Starless: Pushed in flight without exploding in the face. 0 = not to give.
	 */
	public static final int FIREWORK_COUNT = 64;

	/**
	 * Missile duration. In survival 1-3 (how much gunpowder in crafting).
	 3 = maximum push of the elite.
	 */
	public static final int FIREWORK_FLIGHT = 3;

	/**
	 Arrows to bow. Infinity does not waste them, but at least one should lie in the inventory.
	 * 64 = full stack. 0 = not to give.
	 */
	public static final int ARROW_COUNT = 64;

	/*Protection on every piece of armor. The survival ceiling is IV. */
	public static final int PROTECTION = 4;
	/*Thorns. Ceiling III. 0 = remove the thorns (armor does not prick in response and breaks more slowly). */
	public static final int THORNS = 3;
	/*Durability. Ceiling III. */
	public static final int UNBREAKING = 3;
	/** Repair. 1 = is, 0 = no. It's based on experience, on an anvil with a book. */
	public static final int MENDING = 1;

	/*Underwater breath, only helmet. Ceiling III. */
	public static final int RESPIRATION = 3;
	/*Submarine, only helmet. 1 = to dig underwater as on land. */
	public static final int AQUA_AFFINITY = 1;
	/** Agile, just stabbing. Ceiling III. A book from an ancient city. */
	public static final int SWIFT_SNEAK = 3;
	/*Weightlessness, only shoes. The ceiling is IV. */
	public static final int FEATHER_FALLING = 4;
	/**
	 Scuba walking, shoes only. Ceiling III.
	 * The icebreaker doesn't work together. Walking is now on, the icebreaker is off (see FROST WALKER).
	 For an icebreaker, set 0 here and FROST WALKER = 2.
	 */
	public static final int DEPTH_STRIDER = 3;
	/*Icebreaker, only shoes. Ceiling II. 0 = off while the underwater walk is on. */
	public static final int FROST_WALKER = 0;
	/*Speed of mind, only shoes. Ceiling III. It goes with underwater walking. */
	public static final int SOUL_SPEED = 3;

	/*Sword sharpness. The ceiling is V. Heavenly punishment is not on the sword: it is on the axe. They don't work together. */
	public static final int SWORD_SHARPNESS = 5;
	/*Sword throwing. Ceiling II. 0 = Remove: the mobs will not fold. */
	public static final int SWORD_KNOCKBACK = 2;
	/*Conspiracy of fire. Ceiling II. */
	public static final int SWORD_FIRE_ASPECT = 2;
	/** Prey. Ceiling III. */
	public static final int SWORD_LOOTING = 3;
	/*Smashing blade. Ceiling III. Only a sword, in Java. */
	public static final int SWORD_SWEEPING = 3;

	/*The power of the bow. The ceiling is V. */
	public static final int BOW_POWER = 5;
	/** Onion recline. Ceiling II. */
	public static final int BOW_PUNCH = 2;
	/*ignition. 1 = the arrow burns. */
	public static final int BOW_FLAME = 1;
	/**
	 Infinity on the bow. 1 = Eat, there is no repair on this bow.
	 In survival, they are not put together.
	 0 = Remove Infinity and return Repair as on the rest of the set.
	 */
	public static final int BOW_INFINITY = 1;

	/** Effectiveness of pickaxe, axe and shovel. The ceiling is V. */
	public static final int TOOL_EFFICIENCY = 5;
	/**
	 Luck on pickaxe, axe and shovel. Ceiling III.
	 Silk touch is not put here: luck does not work on one thing.
	 */
	public static final int TOOL_FORTUNE = 3;
	/*The heavenly punishment of the axe. The ceiling is V. The axe is not sharp. */
	public static final int AXE_SMITE = 5;

	// ========================================================================

	private SurvivalKit() {
	}

	/*Puts the set to the player who entered the team. The console doesn't work. */
	public static int give(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("You need to enter the command yourself, not from the console."));
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			source.sendFailure(Component.literal("Team only on the server"));
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

		source.sendSuccess(() -> Component.literal("Survival kit put in inventory"), true);
		return 1;
	}

	/*Protection, spikes, strength and repair - on every piece of armor. Plus the charm of this slot. */
	private static ItemStack armor(ServerLevel level, Item item, Level... extra) {
		Level[] all = new Level[4 + extra.length];
		all[0] = enchant(Enchantments.PROTECTION, PROTECTION);
		all[1] = enchant(Enchantments.THORNS, THORNS);
		all[2] = enchant(Enchantments.UNBREAKING, UNBREAKING);
		all[3] = enchant(Enchantments.MENDING, MENDING);
		System.arraycopy(extra, 0, all, 4, extra.length);
		return enchanted(level, item, 1, all);
	}

	/*Strength and repair are in the sword, tools and elite. The onions are assembled separately. */
	private static ItemStack tool(ServerLevel level, Item item, Level... extra) {
		Level[] all = new Level[2 + extra.length];
		all[0] = enchant(Enchantments.UNBREAKING, UNBREAKING);
		all[1] = enchant(Enchantments.MENDING, MENDING);
		System.arraycopy(extra, 0, all, 2, extra.length);
		return enchanted(level, item, 1, all);
	}

	/**
	 Infinity bow doesn't get repaired.
	 * If BOW INFINITY = 0, it is replaced by Repair.
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
				// 0 in constant above = this spell on things is not.
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

	/*Rocket without a star: only gunpowder and paper, for the elite. */
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
			// False is not a throw from the hand, a thing falls under the feet.
			// SERVER ONLY – the ejection is decided by the server (team), the client did not anticipate it.
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static Level enchant(ResourceKey<Enchantment> key, int level) {
		return new Level(key, level);
	}

	private record Level(ResourceKey<Enchantment> key, int level) {
	}
}
