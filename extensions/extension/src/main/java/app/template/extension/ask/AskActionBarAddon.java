package app.template.extension.ask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.function.Consumer;
import android.util.Log;

public final class AskActionBarAddon {
    private static final String TAG = "AskActionBarAddon";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> buttonRef = new WeakReference<>(null);
    private static String currentVideo = "";
    private static boolean registered;
    private AskActionBarAddon() {}

    public static void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> api = Class.forName("app.morphe.extension.youtube.addon.AddOnApi");
            api.getMethod("addVideoIdListener", Consumer.class)
                .invoke(null, (Consumer<String>) AskActionBarAddon::onVideoId);
            Log.i(TAG, "Registered with official Morphe AddOnApi");
        } catch (Exception e) {
            Log.e(TAG, "Cannot register Morphe add-on", e);
        }
    }

    private static void onVideoId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) return;
        currentVideo = id;
        for (long delay : new long[] {0, 300, 750, 1600, 3000})
            MAIN.postDelayed(AskActionBarAddon::tryAttach, delay);
    }

    private static int dp(Context c, int n) {
        return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
    }

    private static Activity getActivity() {
        try {
            Class<?> utils = Class.forName("app.morphe.extension.shared.Utils");
            return (Activity) utils.getMethod("getActivity").invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void tryAttach() {
        try {
            Activity a = getActivity();
            if (a == null || a.isFinishing() || a.getWindow() == null) return;
            View old = buttonRef.get();
            if (old != null && old.isAttachedToWindow()) return;
            View root = a.getWindow().getDecorView();
            View share = findShare(root, root.getHeight(), 0);
            if (share == null || !(share.getParent() instanceof LinearLayout)) return;
            LinearLayout row = (LinearLayout) share.getParent();
            if (row.getOrientation() != LinearLayout.HORIZONTAL ||
                row.getChildCount() < 2 || row.getChildCount() > 12) return;
            for (int i=0; i<row.getChildCount(); i++) {
                if ("ask.addon.direct".equals(row.getChildAt(i).getTag())) return;
            }
            TextView button = new TextView(a);
            button.setTag("ask.addon.direct");
            button.setText("✦ Demander");
            button.setTextSize(12);
            button.setContentDescription("Ouvrir Ask Gemini");
            button.setGravity(android.view.Gravity.CENTER);
            button.setMinHeight(dp(a, 44));
            button.setPadding(dp(a, 8), 0, dp(a, 8), 0);
            if (share instanceof TextView)
                button.setTextColor(((TextView)share).getCurrentTextColor());
            else button.setTextColor(Color.WHITE);
            button.setOnClickListener(v -> openAsk(currentVideo));
            int pos = Math.min(row.getChildCount(), row.indexOfChild(share) + 1);
            row.addView(button, pos, new LinearLayout.LayoutParams(-2, dp(a, 44)));
            buttonRef = new WeakReference<>(button);
            Log.i(TAG, "Ask button inserted");
        } catch (Exception e) {
            Log.e(TAG, "View insertion failed", e);
        }
    }

    private static View findShare(View view, int maxY, int depth) {
        if (view == null || depth > 24 || view.getVisibility() != View.VISIBLE) return null;
        CharSequence desc = view.getContentDescription();
        if (desc != null && view.isShown() && view.getHeight() > 0) {
            String name = desc.toString().trim().toLowerCase(Locale.ROOT);
            if (name.equals("partager") || name.equals("share")) {
                int[] p = new int[2];
                view.getLocationInWindow(p);
                if (p[1] > 0 && p[1] < maxY * .72f) return view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) {
                View child = findShare(group.getChildAt(i), maxY, depth+1);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static void openAsk(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) return;
        try {
            Class<?> official = Class.forName(
                "app.morphe.extension.youtube.patches.ask.AskFlyoutMenuPatch");
            Method open = official.getDeclaredMethod("openAskPanel", String.class);
            open.setAccessible(true);
            open.invoke(null, id);
        } catch (Exception e) {
            Log.e(TAG, "Official Ask flyout menu required for Ask", e);
        }
    }
}
