// Build description for MyReceptionPlanner.
//
// Everyday commands (run from the project folder):
//   ./gradlew test      run the automated tests (test/java)
//   ./gradlew run       compile and start the app
//   ./gradlew build     compile, test and build the jar
//   ./gradlew packageApp -PappVersion=1.0.0
//                       build an installer with its own Java (see below)

plugins {
    // "application" adds the java plugin (compile, jar) plus the run task
    application
}

java {
    // compile for Java 21, whatever Java version runs Gradle
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Libraries are downloaded from Maven Central and cached in ~/.gradle
repositories {
    mavenCentral()
}

// The source lives directly in src/ rather than Gradle's usual
// src/main/java, as in MyContacts.
sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test/java"))
        resources.setSrcDirs(listOf("test/resources"))
    }
}

// Each line is "group:name:version", as listed on https://central.sonatype.com
dependencies {
    implementation("com.formdev:flatlaf:3.7.2")             // the look
    implementation("com.miglayout:miglayout-swing:11.5.1")  // form layouts
    implementation("org.dom4j:dom4j:2.2.0")                 // occasion.xml and the event files
    implementation("com.itextpdf:itextpdf:5.5.13.6")        // PDF reports, as in MyContacts
    implementation("com.github.weisj:jsvg:2.1.0")           // drawing the occasion's SVG emblem

    // JUnit, for the tests only
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.lerot.myreceptionplanner.Main"
}

tasks.test {
    useJUnitPlatform()
    // show each test's result, and the output of failing tests
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// One self-contained jar (the app and every library unpacked into it), so
// it runs with "java -jar MyReceptionPlanner.jar"
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

tasks.jar {
    archiveFileName = "MyReceptionPlanner.jar"
    manifest {
        attributes["Main-Class"] = application.mainClass
        // the version shown in the title bar; -PappVersion=1.2.0
        attributes["Implementation-Version"] = appVersion
    }
    val runtimeJars = configurations.runtimeClasspath
    dependsOn(runtimeJars)
    from({ runtimeJars.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    // signature files from signed libraries would make the merged jar
    // fail its signature check, so leave them out
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ./gradlew packageApp builds an installer that brings its own Java, so it
// runs on a computer without Java: a .deb on Linux, an .msi on Windows
// (jpackage can only make a package for the system it runs on; the GitHub
// workflow .github/workflows/package.yml builds them on release). It goes
// to build/package. Options:
//   -PappVersion=1.2.0          the version (default 1.0.0)
//   -PpackageType=app-image     just the runnable folder, no installer, to try
val windows = System.getProperty("os.name").startsWith("Windows")

tasks.register("packageApp") {
    group = "distribution"
    description = "Builds an installer with its own Java in build/package"
    dependsOn(tasks.jar)
    // local copies: the task must not refer to the build script itself
    val windows = windows
    val version = appVersion
    val jpackage = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
        .map { it.metadata.installationPath.file(if (windows) "bin/jpackage.exe" else "bin/jpackage").asFile.path }
    val type = providers.gradleProperty("packageType").getOrElse(if (windows) "msi" else "deb")
    val out = layout.buildDirectory.dir("package").get().asFile
    val input = layout.buildDirectory.dir("package-input").get().asFile
    val jar = tasks.jar.get().archiveFile.get().asFile
    val icon = file(if (windows) "packaging/myreceptionplanner.ico" else "packaging/myreceptionplanner.png").path
    doLast {
        out.deleteRecursively()
        input.deleteRecursively()
        jar.copyTo(File(input, jar.name))
        val args = mutableListOf(jpackage.get(),
            "--type", type,
            "--name", "MyReceptionPlanner",
            "--app-version", version,
            "--vendor", "lerot",
            "--description", "Plan the tables, seating, menus and messages for a reception",
            "--input", input.path,
            "--main-jar", jar.name,
            "--main-class", "org.lerot.myreceptionplanner.Main",
            "--dest", out.path,
            // the parts of Java the app and its libraries use (from jdeps)
            "--add-modules", listOf("java.base", "java.desktop", "java.logging", "java.prefs",
                "java.sql", "java.xml", "java.naming", "jdk.unsupported", "jdk.charsets",
                "jdk.localedata", "jdk.zipfs").joinToString(","),
            "--jlink-options", "--strip-debug --no-man-pages --no-header-files --include-locales=en")
        if (File(icon).exists()) args += listOf("--icon", icon)
        if (type != "app-image") args += if (windows) listOf(
            "--win-menu", "--win-menu-group", "MyReceptionPlanner", "--win-shortcut",
            // installs for the user only, so no administrator is needed
            "--win-per-user-install", "--win-dir-chooser",
            // the same for every version, so a new one replaces the old
            "--win-upgrade-uuid", "7d1f2b9e-5c43-4e8a-9a61-2f0c8b3e4d17")
        else listOf(
            "--linux-shortcut", "--linux-menu-group", "Office", "--linux-app-category", "office",
            "--linux-package-name", "myreceptionplanner")
        val result = ProcessBuilder(args).inheritIO().start().waitFor()
        if (result != 0) throw GradleException("jpackage failed ($result)")
        println("package in " + out)
    }
}
