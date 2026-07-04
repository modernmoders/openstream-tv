package dev.openstream.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. @HiltAndroidApp triggers Hilt's code generation;
 * all dependency injection containers hang off this class.
 */
@HiltAndroidApp
class OpenStreamApp : Application()
