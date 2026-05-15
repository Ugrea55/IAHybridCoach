// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // CLAUDE CODE: plugin de Google Services. Necesario para que Firebase
    // lea google-services.json en tiempo de compilacion. Se aplica en :app.
    alias(libs.plugins.google.services) apply false
}