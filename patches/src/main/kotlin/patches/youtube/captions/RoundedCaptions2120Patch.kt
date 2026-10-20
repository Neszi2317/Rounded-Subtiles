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
 * Targets the original YouTube 21.20.405 APK (versionCode 1561173354).
 * Inspected classes2.dex in the supplied APK:
 *   Lapdy;.onLayout(ZIIII)V: iget-boolean v7, v0, Lapdy;->b:Z
 *   conditional branch to Path.addRoundRect versus Path.addRect.
 */
private object PhoneRoundedBackgroundFingerprint : Fingerprint(
    definingClass = "Lapdy;",
    name = "onLayout",
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
    filters = listOf(
        fieldAccess(smali = "Lapdy;->b:Z"),
        methodCall(definingClass = "Landroid/graphics/Path;", name = "addRoundRect"),
    ),
)

/**
 * On this specific version, SubtitleWindowView.onMeasure has ONE
 * static field read of ALIGN_NORMAL (not two as in newer versions).
 * Match this exact field access, and change only its destination field.
 */
private object PhoneCaptionAlignmentFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/player/subtitles/ui/SubtitleWindowView;",
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(
        fieldAccess(smali = "Landroid/text/Layout\$Alignment;->ALIGN_NORMAL:Landroid/text/Layout\$Alignment;"),
    ),
)

@Suppress("unused")
val roundedCaptionsPhone2120Patch = bytecodePatch(
    name = "Rounded YouTube Captions (21.20.405)",
    description = "Experimental: force rounded caption backgrounds and center caption text on YouTube 21.20.405.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "YouTube",
            packageName = "com.google.android.youtube",
            apkFileType = ApkFileType.APK,
            appIconColor = 0xFF0033,
            targets = listOf(AppTarget(version = "21.20.405", isExperimental = true)),
        )
    )

    execute {
        val roundedMethod = PhoneRoundedBackgroundFingerprint.method
        val roundedIndex = PhoneRoundedBackgroundFingerprint.instructionMatches[0].index
        val roundedFieldGet = roundedMethod.getInstruction<TwoRegisterInstruction>(roundedIndex)
        check(roundedFieldGet.registerA == 7 && roundedFieldGet.registerB == 0) {
            "Unexpected subtitle background field registers; aborting."
        }
        check(roundedMethod.getInstruction<Instruction>(roundedIndex).opcode == Opcode.IGET_BOOLEAN) {
            "Unexpected subtitle background instruction; aborting."
        }
        check(roundedMethod.getInstruction<Instruction>(roundedIndex + 1).opcode == Opcode.IF_EQZ) {
            "Expected rounded subtitle conditional is missing; aborting."
        }
        roundedMethod.replaceInstruction(roundedIndex, "const/4 v7, 0x1")

        val alignmentMethod = PhoneCaptionAlignmentFingerprint.method
        val alignmentIndex = PhoneCaptionAlignmentFingerprint.instructionMatches[0].index
        val old = alignmentMethod.getInstruction<Instruction>(alignmentIndex)
        check(old.opcode == Opcode.SGET_OBJECT && old is ReferenceInstruction &&
            old.reference is FieldReference) {
            "Expected static alignment field read missing; aborting."
        }
        val field = (old as ReferenceInstruction).reference as FieldReference
        check(field.definingClass == "Landroid/text/Layout\$Alignment;" &&
            field.name == "ALIGN_NORMAL") {
            "Unexpected text alignment field; aborting."
        }
        val register = (old as OneRegisterInstruction).registerA
        alignmentMethod.replaceInstruction(
            alignmentIndex,
            "sget-object v$register, Landroid/text/Layout\$Alignment;->ALIGN_CENTER:Landroid/text/Layout\$Alignment;"
        )
    }
}
