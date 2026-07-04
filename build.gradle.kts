// Root build file: declares plugin versions (from gradle/libs.versions.toml)
// without applying them. Each module applies what it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Build outputs go to build.nosync/: the ".nosync" suffix tells macOS iCloud
// Drive to skip the folder. Building inside an iCloud-synced directory
// (e.g. ~/Documents) otherwise corrupts intermediates — iCloud creates
// "name 2.class" conflict copies mid-build and D8 fails. Found the hard way;
// see DECISIONS.md #8. Harmless on Linux/CI/Windows.
allprojects {
    layout.buildDirectory.set(layout.projectDirectory.dir("build.nosync"))
}
