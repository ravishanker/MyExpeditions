package com.raywenderlich.myexpeditions

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Session

class MainActivity : AppCompatActivity() {

    private lateinit var defaultConfig: Config
    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startArcoreSession()

    }

    private fun startArcoreSession() {
        session = Session(this)

        // Create default config, check is supported, create session from that config.
        defaultConfig = Config.createDefaultConfig()

        if (!session.isSupported(defaultConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, "Yay! This device does support AR", Toast.LENGTH_LONG).show()
    }
}
