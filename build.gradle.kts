buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.forkOptions.jvmArgs?.add("-Dsun.zip.disableMemoryMapping=true")
        options.compilerArgs.addAll(
            listOf(
                "-XDuseOptimizedZip=false"
            )
        )
    }
}
