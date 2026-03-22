dependencies {
    api(project(":module-commom"))

    implementation(platform(libs.spring.ai.bom))
    implementation(enforcedPlatform(libs.grpc.bom))
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.qdrant)
    implementation(libs.spring.ai.chatclient)
    implementation(libs.grpc)
    implementation(libs.jsoup)
}
