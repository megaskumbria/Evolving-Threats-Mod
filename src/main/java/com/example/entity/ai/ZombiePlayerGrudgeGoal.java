package com.example.entity.ai;

import com.example.grudge.PlayerGrudge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Держит цель на игроке из «памяти», когда ваниль её сбросила (нет прямой видимости).
 * Приоритет 0 — выше атаки и похода к свету. Флаг TARGET: не мешает движению, только цель.
 */
public class ZombiePlayerGrudgeGoal extends Goal {

	private final Zombie zombie;

	public ZombiePlayerGrudgeGoal(Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		// Первый контакт: ваниль только что выставила игрока в цель — запоминаем сразу.
		LivingEntity current = zombie.getTarget();
		if (current instanceof Player player) {
			PlayerGrudge.rememberPlayer(zombie, player);
		}
		return PlayerGrudge.resolveGrudgePlayer(zombie) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		// Новая ванильная цель — игрок: обновляем память.
		LivingEntity current = zombie.getTarget();
		if (current instanceof Player player) {
			PlayerGrudge.rememberPlayer(zombie, player);
		}

		ServerPlayer grudge = PlayerGrudge.resolveGrudgePlayer(zombie);
		if (grudge == null) {
			return;
		}
		if (zombie.getTarget() != grudge) {
			zombie.setTarget(grudge);
		}
		// Продлеваем охоту, если снова видим игрока (ваниль сама держит target).
		if (current == grudge) {
			PlayerGrudge.rememberPlayer(zombie, grudge);
		}
	}
}
