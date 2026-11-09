package dev.kraken.hud;

import java.util.ArrayList;
import java.util.List;

/** Toast notifications shown in the HUD (replaces chat messages). */
public final class Notifications {
    public record Toast(String title, String message, int color, long created, float duration) {}

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static final int MAX = 6;

    private Notifications() {}

    public static synchronized void push(String title, String message, int color) {
        push(title, message, color, 3.0f);
    }

    public static synchronized void push(String title, String message, int color, float seconds) {
        // the same title replaces the old toast (e.g. toggling a module quickly)
        TOASTS.removeIf(t -> t.title().equals(title) && t.message().length() < 24 && message.length() < 24);
        TOASTS.add(new Toast(title, message, color, System.currentTimeMillis(), seconds));
        while (TOASTS.size() > MAX) TOASTS.remove(0);
    }

    /** Active toasts, oldest first; expired ones are removed. */
    public static synchronized List<Toast> active() {
        long now = System.currentTimeMillis();
        TOASTS.removeIf(t -> now - t.created() > (t.duration() + 0.4f) * 1000);
        return new ArrayList<>(TOASTS);
    }
}
