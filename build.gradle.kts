plugins {
    alias(libs.plugins.fabric.loom)
}

version = "1.0.0"
group = "com.example.addon"

repositories {
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
}

dependencies {
    // Minecraft
    minecraft(libs.minecraft)
    mappings(libs.yarn.mappings)

    // Fabric
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    // Meteor
    modImplementation(libs.meteor.client)

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.base.archivesName.get()}" }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
