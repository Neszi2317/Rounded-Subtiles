group = "patches.youtube.captions"

patches {
    about {
        name = "Rounded Captions"
        description = "Experimental rounded caption background renderer for YouTube 21.42.241"
        source = "https://github.com/Neszi2317/Rounded-Subtiles"
        author = "Neszi2317"
        contact = "https://github.com/Neszi2317/Rounded-Subtiles/issues"
        website = "https://github.com/Neszi2317/Rounded-Subtiles"
        license = "GPLv3"
    }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }
    publish {
        dependsOn("generatePatchesList")
    }
}
