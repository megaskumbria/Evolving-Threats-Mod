package com.example.skeleton;

import com.example.threat.ThreatScale;
import com.example.threat.ThreatTable;

import com.example.entity.TacticianSkeletonEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Normal skeletons and strays sometimes get a bow with Punch V or VI.
 * Vanilla caps at II; the captain has IV separately. This bow knocks back farther still.
 * Such bows never drop for the player — no chance, no Looting.
 * Punch I–II from normal enchants can still drop as before.
 */
public final class SkeletonPunchBow {

	// ===================== SETTINGS (change these numbers) =====================

	/**
	 * Enable punch bows. false — new skeletons do not get V/VI.
	 * Already given bows still never drop regardless of this flag.
	 */
	public static final boolean ENABLED = true;

	/**
	 * Chance (0.0–1.0) for Punch V or VI on a bow skeleton or stray.
	 * 0.12 = 12% on day 100 (GROWING: 10× lower at start). Disabled until
	 * ThreatTable.STRONG_BOW_DAY (20). Rolled after normal enchants. Captain, escorts, and tactician skip this roll.
	 * 1.0 = every bow skeleton. 0 = never grant.
	 */
	public static final float CHANCE = 0.12f;

	/** Punch V. Stronger than captain IV — player flies farther. */
	public static final int PUNCH_LEVEL_V = 5;

	/** Punch VI. Rarer than V and hits harder. */
	public static final int PUNCH_LEVEL_VI = 6;

	/**
	 * Among strong punch bows: weight for V vs VI.
	 * 3 and 1 = 75% get V, 25% get VI.
	 * VI weight 0 = always V.
	 */
	public static final int LEVEL_V_WEIGHT = 3;

	/** See LEVEL_V_WEIGHT. */
	public static final int LEVEL_VI_WEIGHT = 1;

	/**
	 * Bows with Punch at this level or higher never drop.
	 * 5 = V and VI never drop. Normal I–II (vanilla max) drop as before.
	 * Set 1 if no Punch bow should ever drop.
	 */
	public static final int MIN_LEVEL_THAT_NEVER_DROPS = 5;

	// ========================================================================

	private static final EquipmentSlot[] HANDS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

	private SkeletonPunchBow() {
	}

	/**
	 * Command spawn at position with this bow — no chance roll.
	 * level — {@link #PUNCH_LEVEL_V} or {@link #PUNCH_LEVEL_VI}.
	 * false — could not create skeleton.
	 */
	public static boolean spawn(ServerLevel level, Vec3 pos, @Nullable LivingEntity lookAt, int punchLevel) {
		AbstractSkeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return false;
		}
		float yaw = lookAt == null ? level.getRandom().nextFloat() * 360.0f : faceYaw(pos, lookAt);
		skeleton.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		skeleton.finalizeSpawn(
				level,
				level.getCurrentDifficultyAt(skeleton.blockPosition()),
				EntitySpawnReason.COMMAND,
				null);
		if (skeleton instanceof TacticianSkeletonEntity tactician && tactician.evolvingthreats$isTactician()) {
			tactician.evolvingthreats$setTactician(false);
			tactician.evolvingthreats$setBackItem(ItemStack.EMPTY);
			tactician.evolvingthreats$setMarkedPlayer(null);
			tactician.evolvingthreats$setMeleeUntil(0L);
		}
		giveBow(skeleton, punchLevel);
		if (lookAt != null) {
			skeleton.setTarget(lookAt);
		}
		level.addFreshEntity(skeleton);
		return true;
	}

	/** Puts a bow with the given Punch level in main hand. Never drops. */
	private static void giveBow(AbstractSkeleton skeleton, int punchLevel) {
		int level = punchLevel >= PUNCH_LEVEL_VI ? PUNCH_LEVEL_VI : PUNCH_LEVEL_V;
		ItemStack bow = new ItemStack(Items.BOW);
		var holder = skeleton.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH);
		EnchantmentHelper.updateEnchantments(bow, mutable -> mutable.set(holder, level));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
		skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		skeleton.reassessWeaponGoal();
	}

	private static float faceYaw(Vec3 from, LivingEntity lookAt) {
		double dx = lookAt.getX() - from.x;
		double dz = lookAt.getZ() - from.z;
		return (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
	}

	/**
	 * End of skeleton loadout. If holding a bow and roll succeeds — add Punch V or VI.
	 * Other bow enchants (Power, Flame) remain.
	 */
	public static void tryApply(AbstractSkeleton skeleton) {
		if (!ENABLED || CHANCE <= 0.0f) {
			return;
		}
		// "Stronger than vanilla enchants": none until STRONG_BOW_DAY,
		// then chance grows from start to end of scale with moon.
		if (!ThreatScale.reached(skeleton.level(), ThreatTable.STRONG_BOW_DAY)) {
			return;
		}
		EquipmentSlot slot = bowSlot(skeleton);
		if (slot == null
				|| skeleton.getRandom().nextFloat() >= ThreatScale.dangerousChance(skeleton.level(), CHANCE)) {
			return;
		}
		int level = rollLevel(skeleton.getRandom());
		if (level <= 0) {
			return;
		}
		ItemStack bow = skeleton.getItemBySlot(slot);
		var holder = skeleton.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH);
		EnchantmentHelper.updateEnchantments(bow, mutable -> mutable.set(holder, level));
		skeleton.setItemSlot(slot, bow);
	}

	/**
	 * Before mob drops items. Bow may have moved hands during fight,
	 * so check both hands at death, not only spawn slot.
	 */
	public static void preventDrop(Mob mob) {
		var registry = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		var punch = registry.getOrThrow(Enchantments.PUNCH);
		int min = Math.max(1, MIN_LEVEL_THAT_NEVER_DROPS);
		for (EquipmentSlot slot : HANDS) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!stack.is(Items.BOW)) {
				continue;
			}
			if (EnchantmentHelper.getItemEnchantmentLevel(punch, stack) >= min) {
				mob.setDropChance(slot, 0.0f);
			}
		}
	}

	private static int rollLevel(RandomSource random) {
		int v = Math.max(0, LEVEL_V_WEIGHT);
		int vi = Math.max(0, LEVEL_VI_WEIGHT);
		int total = v + vi;
		if (total <= 0) {
			return PUNCH_LEVEL_V;
		}
		if (random.nextInt(total) < v) {
			return PUNCH_LEVEL_V;
		}
		return PUNCH_LEVEL_VI;
	}

	private static @Nullable EquipmentSlot bowSlot(AbstractSkeleton skeleton) {
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			return EquipmentSlot.MAINHAND;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			return EquipmentSlot.OFFHAND;
		}
		return null;
	}
}
