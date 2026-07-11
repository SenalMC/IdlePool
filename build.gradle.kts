plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "cn.guajichi"
version = "1.0.0-rc.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.devs.beer/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("dev.lone:api-itemsadder:4.0.10")

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
        from("LICENSE") {
            into("META-INF")
        }
        from("NOTICE") {
            into("META-INF")
        }
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    val validateIaAssets = register("validateIaAssets") {
        group = "verification"
        description = "Validates required ItemsAdder textures, dimensions, alpha channels and IDs."
        inputs.dir("itemsadder-pack/contents/idlepool")
        doLast {
            val root = file("itemsadder-pack/contents/idlepool")
            val expected = mapOf(
                "textures/font/icons/clock.png" to (16 to 16),
                "textures/font/icons/gift.png" to (16 to 16),
                "textures/font/icons/coin.png" to (16 to 16),
                "textures/item/start_button.png" to (32 to 32),
                "textures/item/info_button.png" to (32 to 32),
                "textures/item/rewards_button.png" to (32 to 32),
                "textures/item/afk_coin.png" to (32 to 32)
            )
            expected.forEach { (relative, dimensions) ->
                val imageFile = root.resolve(relative)
                check(imageFile.isFile) { "Missing ItemsAdder texture: $relative" }
                val image = javax.imageio.ImageIO.read(imageFile)
                    ?: error("Invalid PNG texture: $relative")
                check(image.width == dimensions.first && image.height == dimensions.second) {
                    "Invalid size for $relative: ${image.width}x${image.height}, expected ${dimensions.first}x${dimensions.second}"
                }
                check(image.colorModel.hasAlpha()) { "Texture must have alpha channel: $relative" }
            }

            val config = root.resolve("idlepool.yml").readText(Charsets.UTF_8)
            listOf(
                "clock", "gift", "coin",
                "start_button", "info_button", "rewards_button", "afk_coin"
            ).forEach { id ->
                check(Regex("(?m)^  ${Regex.escape(id)}:").containsMatchIn(config)) {
                    "Missing ItemsAdder resource id: idlepool:$id"
                }
            }
        }
    }

    register<Zip>("itemsAdderPack") {
        group = "distribution"
        description = "Packages the IdlePool ItemsAdder contents directory."
        dependsOn(validateIaAssets)
        archiveFileName.set("IdlePool-ItemsAdder-${project.version}.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        from("itemsadder-pack/contents") {
            into("contents")
        }
        from("itemsadder-pack/README.md")
        from("itemsadder-pack/install.ps1")
        from("LICENSE")
        from("NOTICE")
    }

    register<Zip>("releaseBundle") {
        group = "distribution"
        description = "Builds a server-ready IdlePool release bundle."
        dependsOn(shadowJar, "itemsAdderPack")
        archiveFileName.set("IdlePool-${project.version}-release.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        from(shadowJar.flatMap { it.archiveFile }) {
            into("plugins")
        }
        from("itemsadder-pack/contents") {
            into("ItemsAdder/contents")
        }
        from("itemsadder-pack/install.ps1") {
            into("ItemsAdder")
        }
        from("itemsadder-pack/LICENSE-ASSETS.md") {
            into("ItemsAdder")
        }
        from("README.md")
        from("CHANGELOG.md")
        from("version.txt")
        from("LICENSE")
        from("NOTICE")
        from("docs/itemsadder.md") {
            into("docs")
        }
        from("docs/rewards.md") {
            into("docs")
        }
        from("docs/release-checklist.md") {
            into("docs")
        }
        from("docs/minebbs-resource-draft.md") {
            into("docs")
        }
        from("docs/minebbs-publish-checklist.md") {
            into("docs")
        }
    }
}
