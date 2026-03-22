plugins {
    `java-library`
    alias(libs.plugins.spring.boot) apply false
}

group = "top.fxmarkbrown.blog"
version = "1.0-SNAPSHOT"

val springBootBom = libs.spring.boot.bom
val lombokDependency = libs.lombok

subprojects {
    apply(plugin = "java-library")

    group = "top.fxmarkbrown.blog"
    version = "1.0-SNAPSHOT"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenLocal()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }

    dependencies {
        api(platform(springBootBom))
        compileOnly(lombokDependency)
        annotationProcessor(lombokDependency)
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.named<ProcessResources>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    sourceSets {
        main {
            resources {
                srcDirs("src/main/resources", "src/main/java")
                exclude("**/*.java")
            }
        }
    }
}
