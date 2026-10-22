package patches.youtube.ask

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction

/**
 * This is an add-on to Morphe, not a fork of official patches.
 * It MUST NOT depend on sharedExtensionPatch or settingsPatch, because
 * those independently invoke the license resource patch twice.
 */
@Suppress("unused")
val askDirectActionBarAddon = bytecodePatch(
    name = "ZZ Ask Gemini under video (Add-on)",
    description = "Experimental: add an Ask button under the video when its Share button is a real View. Requires official Ask flyout menu enabled.",
    default = false,
) {
    compatibleWith(
        Compatibility(
            name = "YouTube",
            packageName = "com.google.android.youtube",
            apkFileType = ApkFileType.APK,
            appIconColor = 0xFF0033,
            targets = listOf(
                AppTarget(version = "21.20.405", isExperimental = true),
                AppTarget(version = "21.42.241", isExperimental = true)
            )
        )
    )

    extendWith("extensions/extension.mpe")

    execute {
        val target = "Lapp/morphe/extension/youtube/addon/AddOnManager;"
        val cls = runCatching { mutableClassDefBy(target) }.getOrElse {
            throw PatchException(
                "Official Morphe AddOnManager was not found. " +
                "Enable the official Ask flyout menu and its dependencies."
            )
        }
        val method = cls.methods.singleOrNull {
            it.name == "registerAddOns" && it.parameterTypes.isEmpty()
        } ?: throw PatchException("Official AddOnManager.registerAddOns() missing")
        method.addInstruction(
            0,
            "invoke-static {}, Lapp/template/extension/ask/AskActionBarAddon;->register()V"
        )
    }
}
