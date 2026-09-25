plugins { id("org.jetbrains.kotlin.jvm") }

kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
}

dependencies { testImplementation("junit:junit:4.13.2") }