plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    // The asset-pack plugin is part of the Android Gradle Plugin. It must be
    // declared here so the :models-pack module can apply it by id.
    id("com.android.asset-pack") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    // Since Kotlin 2.0 the Compose compiler is a Kotlin plugin of its own.
    // Without it, composable functions fail to compile with an internal
    // backend error about not being able to inline Column.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
