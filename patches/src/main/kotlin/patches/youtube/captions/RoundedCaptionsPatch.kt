package patches.youtube.captions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Experimental for YouTube 21.42.241. Keep the curved background and
 * center text in it when the caption renderer would force ALIGN_NORMAL.
 */
private object RoundedBackgroundFingerprint : Fingerprint(
    definingClass = "Laspw;",
    name = "onLayout",
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
    filters = listOf(
        fieldAccess(smali = "Laspw;->b:Z"),
        methodCall(definingClass = "Landroid/graphics/Path;", name = "addRoundRect"),
    ),
)

/**
 * Two branches of SubtitleWindowView.onMeasure replace the default centered
 * StaticLayout with ALIGN_NORMAL for grouped/multiline captions.
 * Both branches need to agree to keep a stable centered appearance.
 */
private object CenteredCaptionFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/player/subtitles/ui/SubtitleWindowView;",
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(
        fieldAccess(smali = "Laspw;->a:Landroid/text/Layout\$Alignment;"),
        fieldAccess(smali = "Laspw;->a:Landroid/text/Layout\$Alignment;"),
    ),
)

@Suppress("unused")
val forceRoundedCaptionsPatch = bytecodePatch(
    name = "Rounded YouTube Captions (21.42.241)",
    description = "Experimental: rounded subtitle backgrounds with centered text.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "YouTube",
            packageName = "com.google.android.youtube",
            apkFileType = ApkFileType.APK,
            appIconColor = 0xFF0033,
            targets = listOf(AppTarget(version = "21.42.241", isExperimental = true)),
        )
    )

    execute {
        // Preserve the previously tested rounded-background behavior.
        val roundedMethod = RoundedBackgroundFingerprint.method
        val roundedIndex = RoundedBackgroundFingerprint.instructionMatches[0].index
        val roundedTarget = roundedMethod.getInstruction<TwoRegisterInstruction>(roundedIndex)
        check(roundedTarget.registerA == 7 && roundedTarget.registerB == 0) {
            "Subtitle rounded path has changed. Aborting safely."
        }
        check(roundedMethod.getInstruction<Instruction>(roundedIndex + 1).opcode == Opcode.IF_EQZ) {
            "Expected rounded path branch is missing. Aborting safely."
        }
        roundedMethod.replaceInstruction(roundedIndex, "const/4 v7, 0x1")

        // Restore centered text in both configurations which force ALIGN_NORMAL.
        val centeredMethod = CenteredCaptionFingerprint.method
        val normalSetters = CenteredCaptionFingerprint.instructionMatches.map { it.index - 1 }
        check(normalSetters.size == 2 && normalSetters.distinct().size == 2) {
            "Expected two subtitle alignment overrides."
        }
        normalSetters.sortedDescending().forEach { index ->
            val old = centeredMethod.getInstruction<Instruction>(index)
            check(old.opcode == Opcode.SGET_OBJECT && old is ReferenceInstruction &&
                old.reference is FieldReference) {
                "Expected a static field read before the subtitle alignment assignment."
            }
            val field = (old as ReferenceInstruction).reference as FieldReference
            check(field.definingClass == "Landroid/text/Layout\$Alignment;" &&
                field.name == "ALIGN_NORMAL") {
                "Caption alignment has changed. Aborting safely."
            }
            val register = (old as OneRegisterInstruction).registerA
            centeredMethod.replaceInstruction(
                index,
                "sget-object v$register, Landroid/text/Layout\$Alignment;->ALIGN_CENTER:Landroid/text/Layout\$Alignment;"
            )
        }
    }
}
