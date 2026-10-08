
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Unit tests for the pure logic (costs, gene tables, quest order file) and module isolation; no Minecraft runtime.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
