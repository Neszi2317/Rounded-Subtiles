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
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/**
 * Experimental patch for the exact original YouTube APK 21.42.241.
 * Forces the existing Path.addRoundRect() branch of subtitle background layout.
 * The actual on-device visual change is not yet verified.
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

@Suppress("unused")
val forceRoundedCaptionsPatch = bytecodePatch(
    name = "Rounded YouTube Captions (21.42.241)",
    description = "Experimental: force the existing rounded background rendering path for captions.",
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
        val method = RoundedBackgroundFingerprint.method
        val index = RoundedBackgroundFingerprint.instructionMatches[0].index
        val target = method.getInstruction<TwoRegisterInstruction>(index)
        check(target.registerA == 7 && target.registerB == 0) {
            "Subtitle layout changed. Aborting safely."
        }
        check(method.getInstruction<Instruction>(index + 1).opcode == Opcode.IF_EQZ) {
            "Expected subtitle branch is missing. Aborting safely."
        }
        method.replaceInstruction(index, "const/4 v7, 0x1")
    }
}
