plugins {
    alias(libs.plugins.spring.boot)
}

tasks.bootJar {
    archiveBaseName.set("module-blog")
    mainClass.set("top.fxmarkbrown.blog.BlogApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
    systemProperty("spring.output.ansi.enabled", "ALWAYS")
}

tasks.jar {
    enabled = false
}

dependencies {
    implementation(project(":module-api"))
    implementation(project(":module-ai"))
    implementation(project(":module-admin"))
    implementation(project(":module-file"))
    implementation(project(":module-quartz"))
    implementation(project(":module-auth"))
}
