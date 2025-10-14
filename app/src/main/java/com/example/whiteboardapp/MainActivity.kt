package com.example.whiteboardapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ToggleButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.view.EraserSizeSelector
import com.example.whiteboardapp.view.WhiteboardView
import com.example.whiteboardapp.view.ZoomControlsView
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel
import androidx.core.graphics.toColorInt
import com.example.whiteboardapp.model.ShapeType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.whiteboardapp.data.WhiteboardRepository
import com.example.whiteboardapp.manager.AutoSaveManager
import com.example.whiteboardapp.manager.ExportManager
import com.example.whiteboardapp.utils.ErrorHandler
import com.example.whiteboardapp.view.ExportDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * The main activity of the application. It hosts the [WhiteboardView] and the toolbar,
 * and it is responsible for initializing the [WhiteboardViewModel] and connecting all UI
 * components to their respective actions in the ViewModel.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: WhiteboardViewModel by viewModels()
    private var autoSaveManager: AutoSaveManager? = null
    private lateinit var whiteboardView: WhiteboardView
    private lateinit var zoomControls: ZoomControlsView
    private lateinit var colorPaletteContainer: View
    private lateinit var eraserSizeSelector: EraserSizeSelector
    private lateinit var strokeWidthSpinner: Spinner
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var penButton: ImageButton
    private lateinit var eraserButton: ImageButton
    private lateinit var selectButton: ImageButton
    private lateinit var lineButton: ImageButton
    private lateinit var rectButton: ImageButton
    private lateinit var circleButton: ImageButton
    private lateinit var textButton: ImageButton
    private lateinit var fillToggle: ToggleButton
    private lateinit var newButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var loadButton: ImageButton
    private lateinit var exportButton: ImageButton
    private lateinit var alignmentButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)

            whiteboardView = findViewById(R.id.whiteboardView)
            // Crucially, provide the ViewModel to the View.
            whiteboardView.setViewModel(viewModel)

            setupZoomControls()
            setupToolbar()
            observeViewModel()

            // Initialize auto-save
            autoSaveManager = AutoSaveManager(
                repository = WhiteboardRepository(),
                intervalMinutes = 5
            )

            autoSaveManager?.startAutoSave(
                scope = lifecycleScope,
                getObjects = { viewModel.objectManager.getObjects() },
                getCurrentSessionId = { viewModel.currentSessionId.value }
            )

        } catch (e: Exception) {
            ErrorHandler.handleException(this, e, "Failed to initialize app") {
                recreate()
            }
        }
    }

    private fun setupZoomControls() {
        zoomControls = findViewById(R.id.zoomControls)

        zoomControls.onZoomIn = {
            whiteboardView.zoomIn()
        }

        zoomControls.onZoomOut = {
            whiteboardView.zoomOut()
        }

        zoomControls.onZoomReset = {
            whiteboardView.resetZoom()
        }

        zoomControls.onZoomFit = {
            whiteboardView.fitToScreen()
        }
    }

    /**
     * Sets up listeners for all the buttons and controls in the toolbar.
     */
    private fun setupToolbar() {
        penButton = findViewById(R.id.btnPen)
        eraserButton = findViewById(R.id.btnEraser)
        undoButton = findViewById(R.id.btnUndo)
        redoButton = findViewById(R.id.btnRedo)
        selectButton = findViewById(R.id.btnSelect)
        lineButton = findViewById(R.id.btnLine)
        rectButton = findViewById(R.id.btnRectangle)
        circleButton = findViewById(R.id.btnCircle)
        textButton = findViewById(R.id.btnText)
        fillToggle = findViewById(R.id.btnFillToggle)
        newButton = findViewById(R.id.btnNew)
        saveButton = findViewById(R.id.btnSave)
        loadButton = findViewById(R.id.btnLoad)
        exportButton = findViewById(R.id.btnExport)
        alignmentButton = findViewById(R.id.btnToggleAlignment)
        alignmentButton.isSelected = true

        penButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Pen)
        }

        eraserButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Eraser)
        }

        undoButton.setOnClickListener {
            animateButtonPress(it)
            viewModel.undo()
        }

        redoButton.setOnClickListener {
            animateButtonPress(it)
            viewModel.redo()
        }

        selectButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Select)
        }

        lineButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Shape(ShapeType.LINE))
        }

        rectButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Shape(ShapeType.RECTANGLE))
        }

        circleButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Shape(ShapeType.CIRCLE))
        }

        textButton.setOnClickListener {
            animateButtonPress(it)
            selectTool(DrawingTool.Text)
        }

        fillToggle.setOnCheckedChangeListener { view, isChecked ->
            animateButtonPress(view)
            viewModel.toggleFill(isChecked)
        }

        newButton.setOnClickListener {
            animateButtonPress(it)
            viewModel.createNewSession()
            Toast.makeText(this, "New canvas created", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            animateButtonPress(it)
            val editText = EditText(this).apply {
                hint = "Enter session name"
            }

            val builder = AlertDialog.Builder(this)
                .setTitle("Save Whiteboard")
                .setView(editText)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save as New") { _, _ ->
                    val name = editText.text.toString().ifBlank { "Untitled Session" }
                    saveAsNewSession(name)
                }

            if (viewModel.currentSessionId.value != null) {
                builder.setNeutralButton("Overwrite") { _, _ ->
                    val name = editText.text.toString().ifBlank { "Untitled Session" }
                    saveCurrentSession(name)
                }
            }
            builder.show()
        }

        loadButton.setOnClickListener { it ->
            animateButtonPress(it)
            lifecycleScope.launch {
                try {
                    val sessions = viewModel.getSessions()
                    val sessionNames = sessions.map { it.name }.toTypedArray()

                    if (sessionNames.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No saved sessions found.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Load Session")
                        .setItems(sessionNames) { _, which ->
                            val selectedSession = sessions[which]
                            loadSession(selectedSession.id)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    ErrorHandler.handleException(this@MainActivity, e, "Failed to load sessions list")
                }
            }
        }

        exportButton.setOnClickListener {
            animateButtonPress(it)
            showExportDialog()
        }

        alignmentButton.setOnClickListener {
            animateButtonPress(it)
            alignmentButton.isSelected = !alignmentButton.isSelected
            whiteboardView.setAlignmentEnabled(alignmentButton.isSelected)
            Toast.makeText(
                this,
                if (alignmentButton.isSelected) "Smart guides enabled" else "Smart guides disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Color Palette
        setupColorPalette()

        // Eraser Selector
        eraserSizeSelector = findViewById(R.id.eraserSizeSelector)
        eraserSizeSelector.onSizeSelected = { radius ->
            viewModel.setEraserRadius(radius)
        }

        // Stroke Width Spinner
        strokeWidthSpinner = findViewById(R.id.strokeWidthSpinner)
        setupStrokeWidthSpinner()

        // Set default tool selection
        selectTool(DrawingTool.Pen)
    }

    /**
     * Animates button press with scale effect for visual feedback
     */
    private fun animateButtonPress(view: View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun selectTool(tool: DrawingTool) {
        viewModel.selectTool(tool)

        // Show/hide zoom controls based on tool
        // Hide zoom controls when using drawing tools to avoid UI clutter
        zoomControls.visibility = when(tool) {
            is DrawingTool.Select -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun updateToolSelection(tool: DrawingTool) {
        // Reset all button backgrounds
        penButton.isSelected = false
        eraserButton.isSelected = false
        selectButton.isSelected = false
        lineButton.isSelected = false
        rectButton.isSelected = false
        circleButton.isSelected = false
        textButton.isSelected = false

        // Highlight selected tool
        when (tool) {
            is DrawingTool.Pen -> {
                penButton.isSelected = true
            }
            is DrawingTool.Eraser -> {
                eraserButton.isSelected = true
            }
            is DrawingTool.Select -> selectButton.isSelected = true
            is DrawingTool.Shape -> {
                when (tool.type) {
                    ShapeType.LINE -> lineButton.isSelected = true
                    ShapeType.RECTANGLE -> rectButton.isSelected = true
                    ShapeType.CIRCLE -> circleButton.isSelected = true
                    else -> {}
                }
            }
            is DrawingTool.Text -> textButton.isSelected = true
            else -> { /* For future tools */ }
        }
    }

    /**
     * Observes LiveData from the ViewModel to update the UI state.
     * For example, it enables/disables the undo/redo buttons based on the command history.
     */
    private fun observeViewModel() {
        viewModel.currentTool.observe(this) { tool ->
            val isPenOrShape = tool is DrawingTool.Pen || tool is DrawingTool.Shape
            val isEraser = tool is DrawingTool.Eraser
            val isShapeTool = tool is DrawingTool.Shape
            val isTextTool = tool is DrawingTool.Text

            // Show/hide appropriate UI elements
            colorPaletteContainer.isVisible = isPenOrShape
            strokeWidthSpinner.isVisible = isPenOrShape
            eraserSizeSelector.isVisible = isEraser
            fillToggle.isVisible = isShapeTool

            // Update tool selection visually
            updateToolSelection(tool)
        }

        viewModel.canUndo.observe(this) { canUndo ->
            undoButton.isEnabled = canUndo
            undoButton.alpha = if (canUndo) 1.0f else 0.4f
        }

        viewModel.canRedo.observe(this) { canRedo ->
            redoButton.isEnabled = canRedo
            redoButton.alpha = if (canRedo) 1.0f else 0.4f
        }

        // Observe fill state to sync the toggle button
        viewModel.isFillEnabled.observe(this) { isEnabled ->
            fillToggle.isChecked = isEnabled
        }
    }

    private fun setupColorPalette() {
        val colorPalette = findViewById<LinearLayout>(R.id.colorPalette)
        colorPaletteContainer = findViewById(R.id.colorPaletteContainer)
        val colors = listOf(
            Color.BLACK,
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.YELLOW,
            Color.MAGENTA,
            Color.CYAN,
            "#FF6600".toColorInt() // Orange
        )

        for ((index, color) in colors.withIndex()) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).also {
                    it.setMargins(12, 12, 12, 12)
                }
                tag = color
                setBackgroundColor(color)

                if (index == 0) {
                    isSelected = true
                    scaleX = 1.2f
                    scaleY = 1.2f
                    elevation = 8f
                }

                setOnClickListener {
                    // Animate color selection
                    animateButtonPress(this)
                    viewModel.setStrokeColor(color)
                    updateColorSelection(this)
                }
            }
            colorPalette.addView(colorView)
        }
    }

    private fun updateColorSelection(selectedView: View) {
        val colorPalette = findViewById<LinearLayout>(R.id.colorPalette)

        for (i in 0 until colorPalette.childCount) {
            val child = colorPalette.getChildAt(i)
            val color = child.tag as Int

            child.isSelected = false
            child.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(150)
                .start()
            child.elevation = 0f
            child.setBackgroundColor(color)
        }

        selectedView.isSelected = true
        selectedView.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(150)
            .start()
        selectedView.elevation = 8f
    }

    private fun setupStrokeWidthSpinner() {
        val strokeWidths = listOf(5f, 10f, 15f, 20f, 30f)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            strokeWidths.map { "${it.toInt()}px" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        strokeWidthSpinner.adapter = adapter

        strokeWidthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setStrokeWidth(strokeWidths[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showExportDialog() {
        ExportDialog(this).show { options ->
            val timestamp = System.currentTimeMillis()
            val extension = when (options.format) {
                ExportManager.ExportFormat.PNG -> "png"
                ExportManager.ExportFormat.JPEG -> "jpg"
                ExportManager.ExportFormat.PDF -> "pdf"
            }

            val outputFile = File(getExternalFilesDir(null), "whiteboard_$timestamp.$extension")

            viewModel.exportCanvas(
                this,
                options,
                outputFile,
                whiteboardView.width * 3, // Canvas size is dynamically adjusted
                whiteboardView.height * 3,
            ) { result ->
                result.onSuccess { file ->
                    Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()

                    // Share the file
                    shareExportedFile(file)
                }.onFailure { error ->
                    Toast.makeText(this, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareExportedFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = when (file.extension) {
                "png", "jpg" -> "image/*"
                "pdf" -> "application/pdf"
                else -> "*/*"
            }
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(android.content.Intent.createChooser(shareIntent, "Share whiteboard"))
    }

    private fun saveCurrentSession(name: String) {
        viewModel.saveCurrentSession(name) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(this, "Session saved successfully", Toast.LENGTH_SHORT).show()
                },
                onFailure = { exception ->
                    ErrorHandler.handleException(this, exception, "Failed to save session") {
                        saveCurrentSession(name)
                    }
                }
            )
        }
    }

    private fun saveAsNewSession(name: String) {
        viewModel.saveAsNewSession(name) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(this, "Saved as new session!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { exception ->
                    ErrorHandler.handleException(this, exception, "Failed to save session") {
                        saveAsNewSession(name)
                    }
                }
            )
        }
    }

    private fun loadSession(sessionId: org.mongodb.kbson.ObjectId) {
        viewModel.loadSession(sessionId) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(this, "Session loaded", Toast.LENGTH_SHORT).show()
                },
                onFailure = { exception ->
                    ErrorHandler.handleException(this, exception, "Failed to load session") {
                        loadSession(sessionId)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        autoSaveManager?.stopAutoSave()
        super.onDestroy()
    }
}