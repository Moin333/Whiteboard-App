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
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel
import androidx.core.graphics.toColorInt
import com.example.whiteboardapp.model.ShapeType

class MainActivity : AppCompatActivity() {

    private val viewModel: WhiteboardViewModel by viewModels()
    private lateinit var whiteboardView: WhiteboardView
    private lateinit var colorPaletteContainer: View
    private lateinit var eraserSizeSelector: EraserSizeSelector
    private lateinit var strokeWidthSpinner: Spinner
    private lateinit var undoButton: ImageButton
    private lateinit var penButton: ImageButton
    private lateinit var eraserButton: ImageButton
    private lateinit var selectButton: ImageButton
    private lateinit var lineButton: ImageButton
    private lateinit var rectButton: ImageButton
    private lateinit var circleButton: ImageButton
    private lateinit var textButton: ImageButton
    private lateinit var fillToggle: ToggleButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        whiteboardView = findViewById(R.id.whiteboardView)
        whiteboardView.setViewModel(viewModel)

        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        // Tool selection buttons
        penButton = findViewById(R.id.btnPen)
        eraserButton = findViewById(R.id.btnEraser)
        undoButton = findViewById(R.id.btnUndo)
        selectButton = findViewById(R.id.btnSelect)
        lineButton = findViewById(R.id.btnLine)
        rectButton = findViewById(R.id.btnRectangle)
        circleButton = findViewById(R.id.btnCircle)
        textButton = findViewById(R.id.btnText)
        fillToggle = findViewById(R.id.btnFillToggle)

        penButton.setOnClickListener {
            selectTool(DrawingTool.Pen)
        }

        eraserButton.setOnClickListener {
            selectTool(DrawingTool.Eraser)
        }

        undoButton.setOnClickListener {
            viewModel.undo()
        }

        selectButton.setOnClickListener {
            selectTool(DrawingTool.Select)
        }

        lineButton.setOnClickListener {
            selectTool(DrawingTool.Shape(ShapeType.LINE))
        }

        rectButton.setOnClickListener {
            selectTool(DrawingTool.Shape(ShapeType.RECTANGLE))
        }

        circleButton.setOnClickListener {
            selectTool(DrawingTool.Shape(ShapeType.CIRCLE))
        }

        textButton.setOnClickListener {
            selectTool(DrawingTool.Text)
        }

        // Fill toggle button setup
        fillToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleFill(isChecked)
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

    private fun selectTool(tool: DrawingTool) {
        viewModel.selectTool(tool)
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
            undoButton.alpha = if (canUndo) 1.0f else 0.5f
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
                // Store the color as a tag for later reference
                tag = color
                setBackgroundColor(color)

                // Add selection indicator for first color (default)
                if (index == 0) {
                    isSelected = true
                    scaleX = 1.2f
                    scaleY = 1.2f
                    elevation = 8f
                }

                setOnClickListener {
                    viewModel.setStrokeColor(color)
                    updateColorSelection(this)
                }
            }
            colorPalette.addView(colorView)
        }
    }

    private fun updateColorSelection(selectedView: View) {
        val colorPalette = findViewById<LinearLayout>(R.id.colorPalette)

        // Reset all color views to unselected state
        for (i in 0 until colorPalette.childCount) {
            val child = colorPalette.getChildAt(i)
            val color = child.tag as Int

            child.isSelected = false
            child.scaleX = 1.0f
            child.scaleY = 1.0f
            child.elevation = 0f
            child.setBackgroundColor(color)
        }

        // Highlight selected color view with scale and elevation
        selectedView.isSelected = true
        selectedView.scaleX = 1.2f
        selectedView.scaleY = 1.2f
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
}