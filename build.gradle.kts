plugins {
    id("com.android.application") version "9.4.0" apply false
}

val syncWebAssets = tasks.register<Sync>("syncWebAssets") {
    from(rootProject.projectDir) {
        include("index.html", "css/**", "img/**", "js/**", "lib/**")
    }
    into(layout.buildDirectory.dir("generated/cm5-assets"))
}
