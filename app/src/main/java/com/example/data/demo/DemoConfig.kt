package com.example.data.demo

import android.content.Context
import com.example.BuildConfig

/**
 * Configuration and safety controls for isolated Demo Mode.
 *
 * Ensures demo data is strictly kept within an isolated demo database and
 * cannot accidentally activate or overwrite real user data in production builds.
 */
object DemoConfig {
    const val DEMO_DATABASE_NAME = "smallstore_demo_database"
    const val PRODUCTION_DATABASE_NAME = "smallstore_database"

    /**
     * Optional programmatic override primarily used by JVM tests and developer settings.
     * When null, the build-specific BuildConfig configuration takes precedence.
     */
    @Volatile
    var demoModeOverride: Boolean? = null

    /**
     * Evaluates whether Demo Mode is active.
     *
     * In release builds (`BuildConfig.DEBUG == false`), this is strictly forced to `false`
     * unless explicitly permitted via [demoModeOverride] during automated tests.
     * In debug/demo builds, this defaults to `BuildConfig.DEMO_MODE`.
     */
    fun isDemoMode(context: Context? = null): Boolean {
        demoModeOverride?.let { return it }
        if (!BuildConfig.DEBUG) {
            return false
        }
        return BuildConfig.DEMO_MODE
    }
}
