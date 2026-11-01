package com.example.skeleton;

import com.example.MobEnchantments;
import com.example.entity.SpawnerHeadCarrierEntity;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Wither skeletons in the Nether sometimes get a netherite sword.
 * Others keep stone swords. Both can get mod enchantments that grow with the threat scale.
 * Netherite sword never drops. XP for any wither skeleton is fixed; the global multiplier does not double it again.
 */
public final class WitherSkeletonSword {

	// ===================== SETTINGS (change these numbers) =====================

	/** false — Nether spawns only stone swords again, no mod enchants. Lower XP below still applies. */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) for netherite sword on day 100. 0.15 = 15%.
	 * GROWING: at start this is 10% of the value (ThreatTable.DANGEROUS_START_RATIO), ~1.5%.
	 * Moon slightly shifts the chance. 0 = no netherite; stone sword and enchants remain.
	 * Nether only. Overworld siege and spawner-head carriers skip this roll.
	 */
	public static final float NETHERITE_CHANCE = 0.15f;

	/**
	 * Chance (0.0–1.0) for sword enchants on day 100. 0.70 = 70%, like normal skeleton swords.
	 * At start ~1% (ThreatTable.WEAPON_ENCHANT_CHANCE_START). Enchant count and level also grow.
	 * Same pool as skeleton swords: Sharpness, Knockback, Fire Aspect, Looting, etc.
	 * 0 = no mod enchants. Stone sword can still drop as in vanilla.
	 */
	public static final float ENCHANT_CHANCE = 0.70f;

	/**
	 * XP when a player kills a wither skeleton. 25.
	 * Vanilla was 5; the mod used to double to 10 and add a bit for the sword.
	 * This replaces that. The global multiplier does not apply twice.
	 * -1 = vanilla XP with multiplier. 0 = no XP.
	 * Nether, siege, and spawner carriers all use the same value.
	 */
	public static final int XP_REWARD = 25;

	// ========================================================================

	private WitherSkeletonSword() {
	}

	/** End of spawn. Sword and enchants — Nether only, not spawner-head carriers. */
	public static void tryApply(WitherSkeleton skeleton) {
		if (!ENABLED || skeleton.level().dimension() != Level.NETHER) {
			return;
		}
		if (skeleton instanceof SpawnerHeadCarrierEntity carrier && carrier.evolvingthreats$isSpawnerHeadCarrier()) {
			return;
		}
		if (skeleton.getRandom().nextFloat() < ThreatScale.dangerousChance(skeleton.level(), NETHERITE_CHANCE)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
			// 0 = never drops, even with Looting. Stone sword drop chance is unchanged.
			skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
		MobEnchantments.rollMainHandGrowing(skeleton, ENCHANT_CHANCE);
	}

	/** XP to award. -1 = not a wither skeleton or vanilla XP again. */
	public static int experienceReward(Mob mob) {
		if (!(mob instanceof WitherSkeleton) || XP_REWARD < 0) {
			return -1;
		}
		return XP_REWARD;
	}
}
