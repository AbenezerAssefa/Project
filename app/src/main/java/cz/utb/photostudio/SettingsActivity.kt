package cz.utb.photostudio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cz.utb.photostudio.config.GlobalConfig
import cz.utb.photostudio.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Refresh UI values
        binding.objDetection.isChecked = GlobalConfig.OBJ_DETECTION_ENABLED
        binding.cameraFlash.isChecked = GlobalConfig.CAMERA_FLASH_MODE
        binding.pictureQuality.progress = GlobalConfig.PICTURE_QUALITY
        binding.pictureQuality.fromCenter = false

        // Events
        binding.applyAndBack.setOnClickListener {
            GlobalConfig.OBJ_DETECTION_ENABLED = binding.objDetection.isChecked
            GlobalConfig.CAMERA_FLASH_MODE = binding.cameraFlash.isChecked
            GlobalConfig.PICTURE_QUALITY = binding.pictureQuality.progress
            GlobalConfig.storeSettings(baseContext)
            finish()
        }
        binding.back.setOnClickListener {
            finish()
        }
    }

}
