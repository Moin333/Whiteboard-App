package com.example.whiteboardapp.view

import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.whiteboardapp.R
import com.example.whiteboardapp.manager.ExportManager

/**
 * A class that builds and displays the "Export Options" dialog.
 * @param onExportConfirmed A callback invoked with the chosen [ExportManager.ExportOptions].
 */
class ExportDialog(private val context: Context) {

    fun show(onExportConfirmed: (ExportManager.ExportOptions) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export, null)

        val formatSpinner = dialogView.findViewById<Spinner>(R.id.spinnerFormat)
        val qualitySeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarQuality)
        val qualityLabel = dialogView.findViewById<TextView>(R.id.textQualityLabel)
        val scaleSpinner = dialogView.findViewById<Spinner>(R.id.spinnerScale)
        val cropCheckBox = dialogView.findViewById<CheckBox>(R.id.checkBoxCrop)
        val watermarkCheckBox = dialogView.findViewById<CheckBox>(R.id.checkBoxWatermark)

        // Setup format spinner
        val formats = ExportManager.ExportFormat.entries.map { it.name }
        formatSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, formats)

        // Setup quality seekbar
        qualitySeekBar.max = 100
        qualitySeekBar.progress = 95
        qualitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                qualityLabel.text = context.getString(R.string.quality_label, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup scale spinner
        val scales = arrayOf("50%", "75%", "100%", "150%", "200%")
        val scaleValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
        scaleSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, scales)
        scaleSpinner.setSelection(2) // Default 100%

        AlertDialog.Builder(context)
            .setTitle("Export Options")
            .setView(dialogView)
            .setPositiveButton("Export") { _, _ ->
                val options = ExportManager.ExportOptions(
                    format = ExportManager.ExportFormat.entries[formatSpinner.selectedItemPosition],
                    quality = qualitySeekBar.progress,
                    scale = scaleValues[scaleSpinner.selectedItemPosition],
                    cropToContent = cropCheckBox.isChecked,
                    includeWatermark = watermarkCheckBox.isChecked
                )
                onExportConfirmed(options)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}