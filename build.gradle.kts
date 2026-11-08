// Build description for MyReceptionPlanner.
//
// Everyday commands (run from the project folder):
//   ./gradlew test      run the automated tests (test/java)
//   ./gradlew run       compile and start the app
//   ./gradlew build     compile, test and build the jar

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
    implementation("org.dom4j:dom4j:2.2.0")                 // tables.xml, guests.xml
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")       // reading MyContacts' database
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
