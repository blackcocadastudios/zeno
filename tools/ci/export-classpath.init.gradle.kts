// Development aid: copies every module's resolved compile classpath (AARs and
// JARs) into build/zeno-classpath/<module>/ so code can be type-checked in an
// environment without Maven access. Not used by the regular build.
//
// usage: ./gradlew --init-script tools/ci/export-classpath.init.gradle.kts exportCompileClasspath --no-configuration-cache

allprojects {
    afterEvaluate {
        val project = this
        val names = listOf(
            "debugCompileClasspath",
            "debugUnitTestCompileClasspath",
            "compileClasspath",
            "testCompileClasspath",
        )
        val dest = rootProject.layout.buildDirectory.dir(
            "zeno-classpath/" + project.path.removePrefix(":").replace(':', '_').ifEmpty { "root" },
        )
        tasks.register("exportCompileClasspath") {
            doLast {
                val out = dest.get().asFile
                out.mkdirs()
                names.mapNotNull { project.configurations.findByName(it) }
                    .filter { it.isCanBeResolved }
                    .forEach { configuration ->
                        val files = configuration.incoming.artifactView { lenient(true) }.files
                        files.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".aar")) }
                            .forEach { file -> file.copyTo(File(out, file.name), overwrite = true) }
                    }
            }
        }
    }
}
