plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Unit tests for the pure logic (costs, gene tables, quest order file) and module isolation; no Minecraft runtime.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Test groups (0.17.0, refactor 6.5): tests tagged "slow" (fake worlds, whole chains, previews, the long plans) run in
// every full build; -PfastOnly leaves them out and -PslowOnly runs only them, for an agent's own package while it
// works. Tests tagged "golden-gen" write golden files and never run in a build.
tasks.test {
    useJUnitPlatform {
        excludeTags("golden-gen")
        if (project.hasProperty("fastOnly")) excludeTags("slow")
        if (project.hasProperty("slowOnly")) includeTags("slow")
    }
}
