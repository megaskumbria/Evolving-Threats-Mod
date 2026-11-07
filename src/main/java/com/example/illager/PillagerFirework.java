package com.example.illager;

import com.example.advancement.ModAdvancements;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 The robber sometimes loads a missile into the crossbow.
 The rocket is placed in the crossbow as it begins to pull the bowstring.
 * Therefore, fireworks are immediately visible on the crossbow.
 The arrow in this volley is not charged.
 A rocket with a star explodes upon hitting and relieves health.
 */
public final class PillagerFirework {

	// ===================== SETTINGS (change these numbers) =====================

	/*False: The thief always shoots an arrow. */
	public static final boolean ENABLED = true;

	/**
	 Chance (0.0-1.0) that the next charge will be a rocket.
	 0.20 = 20%, approximately one in five volleys. The rest of the volleys are an ordinary arrow.
	 * Throw at the start of charging. 0 = no missiles. 1 = each shot a rocket.
	 */
	public static final float SHOT_CHANCE = 0.20f;

	/**
	 How many stars are in the rocket. This damage, no armor, normal difficulty.
	 1 = 7 health (3.5 hearts). 2 = 9. 3 = 11. Each star after the first adds 2.
	 7 = the limit that can be collected in the game, 19 health.
	 0 = Do not load the rocket, there is an arrow.
	 A light stroke is weaker, a difficult one stronger. Reduces armor.
	 The explosion affects everyone nearby, about five blocks, including the robber himself.
	 */
	public static final int STAR_COUNT = 1;

	/**
	 * Where the missile is flying in the height of the target. 0.0 in the legs, 1.0 in the top.
	 0.45 in the chest. Above 0.7 is often overhead again:
	 The rocket flies straight and does not fall, unlike an arrow.
	 */
	public static final double AIM_BODY = 0.45;

	// ========================================================================

	/*There are no more than seven stars in a conventional rocket. */
	private static final int MAX_STARS = 7;

	private PillagerFirework() {
	}

	/**
	 The robber just started pulling the crossbow.
	 The rocket is already in it: the crossbow model shows the fireworks all charging.
	 The player and the piglin don't touch.
	 */
	public static void onChargeStart(LivingEntity shooter, InteractionHand hand) {
		if (!(shooter instanceof Pillager) || shooter.level().isClientSide() || shooter.isUsingItem()) {
			return;
		}
		ItemStack crossbow = shooter.getItemInHand(hand);
		if (!crossbow.is(Items.CROSSBOW)) {
			return;
		}
		if (!ENABLED || SHOT_CHANCE <= 0.0f || STAR_COUNT <= 0) {
			clearRocket(crossbow);
			return;
		}
		if (shooter.getRandom().nextFloat() < SHOT_CHANCE) {
			crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(rocket())));
		} else {
			clearRocket(crossbow);
		}
	}

	/**
	 The game aims as if the projectile will fall like an arrow: the further the target, the higher the arc.
	 The missile does not fall and flies overhead. Here the arc is removed, the target is the chest.
	 Speed and scatter remain what the game gave. The arrow doesn't touch.
	 */
	public static void correctAim(LivingEntity shooter, Projectile projectile, LivingEntity target, float power, float uncertainty, float angle) {
		if (!ENABLED || target == null || !(shooter instanceof Pillager) || !(projectile instanceof FireworkRocketEntity)) {
			return;
		}
		Vec3 toTarget = new Vec3(target.getX() - projectile.getX(), target.getY(AIM_BODY) - projectile.getY(), target.getZ() - projectile.getZ());
		if (toTarget.lengthSqr() < 1.0E-4) {
			return;
		}
		if (angle != 0.0f) {
			Vec3 side = new Vec3(-toTarget.z, 0.0, toTarget.x);
			if (side.lengthSqr() > 1.0E-4) {
				double fan = Math.tan(angle * (Math.PI / 180.0)) * toTarget.horizontalDistance();
				side = side.normalize().scale(fan);
				toTarget = toTarget.add(side.x, 0.0, side.z);
			}
		}
		projectile.shoot(toTarget.x, toTarget.y, toTarget.z, power, uncertainty);
	}

	/**
	 * The rocket already tried to hurt whoever it could see.
	 * A miss leaves nobody freshly hurt, so it does not count.
	 * 5 blocks is the blast the game uses. 25 is that distance squared.
	 */
	public static void onExplosionHit(FireworkRocketEntity rocket, ServerLevel level) {
		if (!ENABLED || !(rocket.getOwner() instanceof Pillager)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || player.hurtTime < 9) {
				continue;
			}
			if (rocket.distanceToSqr(player) <= 25.0) {
				ModAdvancements.onCrossbowFlash(player);
			}
		}
	}

	/*Remove the rocket left over from the last volley so that this volley will charge the arrow. */
	private static void clearRocket(ItemStack crossbow) {
		ChargedProjectiles charged = crossbow.get(DataComponents.CHARGED_PROJECTILES);
		if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) {
			crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
		}
	}

	private static ItemStack rocket() {
		int stars = Math.clamp(STAR_COUNT, 1, MAX_STARS);
		List<FireworkExplosion> explosions = new ArrayList<>(stars);
		IntList color = IntList.of(DyeColor.RED.getFireworkColor());
		FireworkExplosion star = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, color, IntList.of(), false, false);
		for (int i = 0; i < stars; i++) {
			explosions.add(star);
		}
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1, explosions));
		return rocket;
	}
}
