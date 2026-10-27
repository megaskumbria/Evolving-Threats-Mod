package com.example.client.threat;

import com.example.EvolvingThreats;
import com.example.threat.ThreatScale;
import com.example.threat.ThreatStartPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.example.threat.ThreatTable;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * Шкала угрозы на экране: слева луна текущего дня, в центре полоска (день / 100), справа череп текущей ступени.
 * По умолчанию висит в ЛЕВОМ ВЕРХНЕМ углу: справа сверху ваниль рисует иконки эффектов зелий, по центру боссбары.
 *
 * <p>Ничего не приходит с сервера: день и фазу луны клиент считает сам по времени мира,
 * которое игра и так присылает ({@link ThreatScale}). Подписи с числом дней нет — только картинки.
 * Картинки лежат в assets/evolving-threats/textures/gui/sprites/threat/ (делает tools/prepare_threat_textures.py).
 */
public final class ThreatHud {

	// ===================== НАСТРОЙКИ (меняй эти числа) =====================

	/** false — шкала на экране не показывается (на самой механике это не сказывается). */
	public static final boolean ENABLED = true;

	/** Отступ от левого края экрана, в пикселях интерфейса. Больше — шкала правее. */
	public static final int MARGIN_LEFT = 4;

	/** Отступ от верхнего края экрана, в пикселях интерфейса. Больше — шкала ниже. */
	public static final int MARGIN_TOP = 4;

	/**
	 * Во сколько раз изменить размер всей шкалы. 1.0 = как нарисовано (164 пикселя в ширину),
	 * 0.75 = поменьше (сейчас), 0.5 = совсем маленькая, 1.5 = побольше. Дробные числа делают картинку чуть мягче.
	 */
	public static final float SCALE = 0.75f;

	/** Показывать ли иконку луны слева. */
	public static final boolean SHOW_MOON = true;

	/** Показывать ли череп справа. */
	public static final boolean SHOW_STAGE_ICON = true;

	/** Не рисовать шкалу, пока открыт экран отладки (F3): он занимает как раз левый верх. */
	public static final boolean HIDE_WHEN_DEBUG_OPEN = true;

	// ========================================================================

	// Размеры картинок. Должны совпадать с файлами (tools/prepare_threat_textures.py пишет их при запуске).
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 2;
	private static final int FRAME_WIDTH = 128;
	private static final int FRAME_HEIGHT = 12;
	/** Окно внутри рамки, куда рисуется заполнение: отступ слева, сверху и размер. */
	private static final int WINDOW_X = 5;
	private static final int WINDOW_Y = 4;
	private static final int WINDOW_WIDTH = 118;
	private static final int WINDOW_HEIGHT = 4;

	private static final Identifier FRAME = EvolvingThreats.id("threat/bar_frame");
	private static final Identifier FILL = EvolvingThreats.id("threat/bar_fill");

	private ThreatHud() {
	}

	/** Вызывается один раз при запуске клиента. */
	public static void register() {
		// Сервер сообщает, с какого момента мир считает дни (иначе в старом мире шкала показала бы весь возраст мира).
		ClientPlayNetworking.registerGlobalReceiver(ThreatStartPayload.TYPE,
				(payload, context) -> ThreatScale.setStartClock(payload.startClock()));
		if (!ENABLED) {
			return;
		}
		HudElementRegistry.addLast(EvolvingThreats.id("threat_bar"), ThreatHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (HIDE_WHEN_DEBUG_OPEN && minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate((float) MARGIN_LEFT, (float) MARGIN_TOP);
		graphics.pose().scale(SCALE, SCALE);

		int x = 0;
		if (SHOW_MOON) {
			sprite(graphics, EvolvingThreats.id("threat/moon_" + ThreatScale.moonPhase(level)), x, 0, ICON_SIZE, ICON_SIZE);
			x += ICON_SIZE + ICON_GAP;
		}

		int barY = (ICON_SIZE - FRAME_HEIGHT) / 2;
		int filled = Math.round(WINDOW_WIDTH * ThreatScale.progress(level));
		if (filled > 0) {
			// Частичная картинка: берём только левую часть полоски, чтобы цвет шёл от зелёного к красному по шкале.
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILL,
					WINDOW_WIDTH, WINDOW_HEIGHT, 0, 0,
					x + WINDOW_X, barY + WINDOW_Y, filled, WINDOW_HEIGHT);
		}
		sprite(graphics, FRAME, x, barY, FRAME_WIDTH, FRAME_HEIGHT);
		x += FRAME_WIDTH + ICON_GAP;

		if (SHOW_STAGE_ICON) {
			sprite(graphics, EvolvingThreats.id("threat/stage_" + stageIndex(level)), x, 0, ICON_SIZE, ICON_SIZE);
		}

		graphics.pose().popMatrix();
	}

	/** Номер иконки-черепа 0–3 по текущему дню. Дни порогов — ThreatTable.STAGE_ICON_FROM_DAY. */
	private static int stageIndex(Level level) {
		int day = ThreatScale.day(level);
		int stage = 0;
		for (int i = 0; i < ThreatTable.STAGE_ICON_FROM_DAY.length; i++) {
			if (day >= ThreatTable.STAGE_ICON_FROM_DAY[i]) {
				stage = i;
			}
		}
		return stage;
	}

	private static void sprite(GuiGraphicsExtractor graphics, Identifier id, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, width, height);
	}
}
