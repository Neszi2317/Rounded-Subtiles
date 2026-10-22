#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(sys.argv[1])
d = root / "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches"
ask_file = d / "ask/AskFlyoutMenuPatch.java"
video_file = d / "VideoInformation.java"
kotlin_file = root / "patches/src/main/kotlin/app/morphe/patches/youtube/interaction/ask/AskFlyoutMenuPatch.kt"

def change(s,old,new,label):
    n=s.count(old)
    if n!=1: raise ValueError(f"{label}: expected exactly 1 anchor, got {n}")
    return s.replace(old,new,1)

a=ask_file.read_text()
v=video_file.read_text()
k=kotlin_file.read_text()
a=change(a,'    private static void openAskPanel(String videoId) {','''    public static void openAskFromActionBar() {
        String id = VideoInformation.getVideoId();
        if (id == null || id.length() != 11) return;
        Utils.runOnMainThread(() -> openAskPanel(id, false));
    }

    private static void openAskPanel(String videoId) {
        openAskPanel(videoId, true);
    }

    private static void openAskPanel(String videoId, boolean autoSummarize) {''','ask signature')
head,rest=a.split('    private static void openAskPanel(String videoId, boolean autoSummarize) {',1)
body,tail=rest.split('    /**\n     * @return If this class gave the processor',1)
body=body.replace('showAskPanel(videoId);','showAskPanel(videoId, autoSummarize);')
a=head+'    private static void openAskPanel(String videoId, boolean autoSummarize) {'+body+'    /**\n     * @return If this class gave the processor'+tail
a=change(a,'    private static void showAskPanel(String videoId) {','    private static void showAskPanel(String videoId, boolean autoSummarize) {','show signature')
a=change(a,'''                pendingWelcomeToken = panel.welcomeToken;
                pendingSummarizeToken = panel.summarizeToken;
                pendingSuggestionSelectionId = panel.summarizeSelectionId;''','''                pendingWelcomeToken = autoSummarize ? panel.welcomeToken : null;
                pendingSummarizeToken = autoSummarize ? panel.summarizeToken : null;
                pendingSuggestionSelectionId = autoSummarize ? panel.summarizeSelectionId : -1;''','summarize state')
v=change(v,'            videoId = newlyLoadedVideoId;\n','            videoId = newlyLoadedVideoId;\n            app.morphe.extension.youtube.patches.ask.AskActionBarButton.onVideoChanged();\n','video callback')
k=change(k,'import app.morphe.patches.youtube.misc.settings.settingsPatch\n','import app.morphe.patches.youtube.misc.settings.settingsPatch\nimport app.morphe.patches.youtube.video.information.videoInformationPatch\n','Kotlin import')
k=change(k,'        versionCheckPatch,\n        authHookPatch','        versionCheckPatch,\n        videoInformationPatch,\n        authHookPatch','Kotlin dependency')

helper=r'''package app.morphe.extension.youtube.patches.ask;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Locale;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.VideoInformation;

/**
 * Experimental View insertion next to Share under the video.
 * Virtual Litho action bars have no View anchor: safely makes no UI changes.
 */
public final class AskActionBarButton {
    private static final String MARKER = "morphe.ask.actionbar.experimental";
    private static WeakReference<View> attached = new WeakReference<>(null);
    private static String lastVideoId = "";
    private AskActionBarButton() {}
    public static void onVideoChanged() {
        String videoId = VideoInformation.getVideoId();
        if (videoId == null || videoId.length() != 11 || videoId.equals(lastVideoId)) return;
        lastVideoId = videoId;
        for (long delay : new long[] {0, 250, 750, 1500, 3000})
            Utils.runOnMainThreadDelayed(AskActionBarButton::tryAttach, delay);
    }
    private static int dp(Context c, int x) {
        return (int)(x*c.getResources().getDisplayMetrics().density + 0.5f);
    }
    public static void tryAttach() {
        try {
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing()) return;
            View prior = attached.get();
            if (prior != null && prior.isAttachedToWindow()) return;
            View root = activity.getWindow().getDecorView();
            View share = findShare(root,root.getHeight(),0);
            if (share == null || !(share.getParent() instanceof LinearLayout row)) return;
            if (row.getOrientation()!=LinearLayout.HORIZONTAL || row.getChildCount()<2 || row.getChildCount()>12) return;
            for (int i=0;i<row.getChildCount();i++)
                if (MARKER.equals(row.getChildAt(i).getTag())) return;
            TextView button = new TextView(activity);
            button.setTag(MARKER);
            button.setText("✦ Demander");
            button.setContentDescription("Demander à propos de cette vidéo");
            button.setTextSize(12);
            button.setGravity(android.view.Gravity.CENTER);
            button.setMinHeight(dp(activity,44));
            button.setPadding(dp(activity,8),0,dp(activity,8),0);
            if (share instanceof TextView text) button.setTextColor(text.getCurrentTextColor());
            button.setOnClickListener(view -> AskFlyoutMenuPatch.openAskFromActionBar());
            row.addView(button,Math.min(row.indexOfChild(share)+1,row.getChildCount()),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(activity,44)));
            attached = new WeakReference<>(button);
            Logger.printDebug(() -> "Ask actionbar experimental inserted");
        } catch(Exception e) {
            Logger.printException(() -> "Ask actionbar insertion failure",e);
        }
    }
    private static View findShare(View v,int height,int depth) {
        if(depth>24 || v.getVisibility()!=View.VISIBLE) return null;
        CharSequence desc=v.getContentDescription();
        if(desc!=null && v.isShown() && v.getHeight()>0) {
            String label=desc.toString().toLowerCase(Locale.ROOT);
            if(label.equals("partager") || label.equals("share")) {
                int[] xy = new int[2];
                v.getLocationInWindow(xy);
                if(xy[1]>0 && xy[1]<height*0.68f) return v;
            }
        }
        if(v instanceof ViewGroup group)
            for(int i=0;i<group.getChildCount();i++) {
                View found=findShare(group.getChildAt(i),height,depth+1);
                if(found!=null) return found;
            }
        return null;
    }
}
'''
a_file=d/"ask/AskActionBarButton.java"
if a_file.exists(): raise ValueError("Refuse to overwrite helper")
ask_file.write_text(a)
video_file.write_text(v)
kotlin_file.write_text(k)
a_file.write_text(helper)
print("Patched: menu preserved + experimental direct Ask button bridge.")
