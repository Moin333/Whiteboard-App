package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.whiteboardapp.R
import com.example.whiteboardapp.model.TextObject

/**
 * A class that builds and displays the dialog for adding or editing a [TextObject].
 * @param onConfirm A callback invoked with the new or updated [TextObject].
 */
class TextEditDialog(
    private val context: Context,
    private val textObject: TextObject?, // Null if creating new text
    private val onConfirm: (TextObject) -> Unit
) {
    private lateinit var editText: EditText
    private lateinit var boldToggle: ToggleButton
    private lateinit var italicToggle: ToggleButton
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var colorSpinner: Spinner
    private lateinit var alignmentGroup: RadioGroup
    private lateinit var textSizeLabel: TextView

    private val colors = mapOf(
        "Black" to Color.BLACK,
        "Red" to Color.RED,
        "Blue" to Color.BLUE,
        "Green" to Color.GREEN
    )
    private val colorNames = colors.keys.toTypedArray()

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_text_edit, null)
        setupViews(view)
        loadTextObjectProperties()

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (textObject == null) "Add Text" else "Edit Text")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                if (editText.text.isNotBlank()) {
                    onConfirm(createTextObjectFromInput())
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        showKeyboard()
    }

    private fun setupViews(view: View) {
        editText = view.findViewById(R.id.editText)
        boldToggle = view.findViewById(R.id.toggleBold)
        italicToggle = view.findViewById(R.id.toggleItalic)
        sizeSeekBar = view.findViewById(R.id.seekBarTextSize)
        colorSpinner = view.findViewById(R.id.spinnerTextColor)
        alignmentGroup = view.findViewById(R.id.radioGroupAlignment)
        textSizeLabel = view.findViewById(R.id.textSizeLabel)

        // Text size (e.g., from 12sp to 120sp)
        sizeSeekBar.max = 108
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                textSizeLabel.text = context.getString(R.string.text_size_label, size)
                editText.textSize = size.toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Color spinner
        colorSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, colorNames)
    }

    private fun loadTextObjectProperties() {
        textObject?.let {
            editText.setText(it.text)
            boldToggle.isChecked = it.isBold
            italicToggle.isChecked = it.isItalic
            sizeSeekBar.progress = (it.textSize - 12).toInt()

            val colorIndex = colors.values.indexOf(it.textColor)
            colorSpinner.setSelection(if (colorIndex != -1) colorIndex else 0)

            when (it.alignment) {
                Paint.Align.LEFT -> alignmentGroup.check(R.id.radioAlignLeft)
                Paint.Align.CENTER -> alignmentGroup.check(R.id.radioAlignCenter)
                Paint.Align.RIGHT -> alignmentGroup.check(R.id.radioAlignRight)
            }
        } ?: run {
            sizeSeekBar.progress = 36 // Default 48sp
        }
    }

    private fun createTextObjectFromInput(): TextObject {
        val alignment = when (alignmentGroup.checkedRadioButtonId) {
            R.id.radioAlignCenter -> Paint.Align.CENTER
            R.id.radioAlignRight -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }

        return textObject?.apply {
            update(
                newText = editText.text.toString(),
                newIsBold = boldToggle.isChecked,
                newIsItalic = italicToggle.isChecked,
                newTextSize = (sizeSeekBar.progress + 12).toFloat(),
                newTextColor = colors[colorNames[colorSpinner.selectedItemPosition]] ?: Color.BLACK,
                newAlignment = alignment
            )
        } ?: TextObject(
            text = editText.text.toString(),
            x = 0f, y = 0f, // Position will be set by the caller
            isBold = boldToggle.isChecked,
            isItalic = italicToggle.isChecked,
            textSize = (sizeSeekBar.progress + 12).toFloat(),
            textColor = colors[colorNames[colorSpinner.selectedItemPosition]] ?: Color.BLACK,
            alignment = alignment
        )
    }

    private fun showKeyboard() {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}