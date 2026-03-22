dependencies {
    api(project(":module-commom"))

    api(libs.x.file.storage)
    implementation(libs.qiniu)
    implementation(libs.aliyun.oss)
    implementation(libs.cos.api)
}
