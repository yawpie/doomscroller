plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.test {
    systemProperty("parityFixtures", rootProject.file("parity-fixtures/gesture-goldens.json").absolutePath)
}
