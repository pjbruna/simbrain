package org.simbrain.util.table

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.simbrain.util.*
import org.simbrain.util.propertyeditor.AnnotatedPropertyEditor
import smile.io.Read
import smile.plot.swing.BoxPlot
import smile.plot.swing.Histogram
import smile.plot.swing.PlotGrid
import javax.swing.JOptionPane

/**
 * Default directory where tables are stored.
 */
private val TABLE_DIRECTORY = "." + Utils.FS + "simulations" + Utils.FS + "tables"

fun SimbrainDataViewer.addSimpleDefaults()  {
    addAction(table.zeroFillAction)
    addAction(table.randomizeAction)
}

val DataViewerTable.randomizeAction
    get() = createAction(
        "menu_icons/Rand.png",
        "Randomize",
        "Randomize selected cells",
        CmdOrCtrl + 'R'
    ) {
        randomizeSelectedCells()
    }

val DataViewerTable.randomizeColumnAction
    get() = createAction(
        "menu_icons/Rand_C.png",
        "Randomize column",
        "Randomize cells in selected column",
    ) {
        model.randomizeColumn(selectedColumn)
    }

val DataViewerTable.zeroFillAction
    get() = createAction(
        "menu_icons/Fill_0.png",
        "Zero Fill",
        "Zero Fill selected cells",
        'Z'
    ) {
        zeroFillSelectedCells()
    }

val DataViewerTable.fillAction
    get() = createAction(
        "menu_icons/Fill.png",
        "Fill...",
        "Fill selected cells"
    ) {
        val fillVal = JOptionPane.showInputDialog(this, "Value:", "0").toDouble()
        fillSelectedCells(fillVal)
    }

val DataViewerTable.editRandomizerAction
    get() = createAction(
        "menu_icons/Prefs.png",
        "Edit randomizer...",
        "Edit table wide randomizer"
    ) {
        val editor = AnnotatedPropertyEditor(model.cellRandomizer)
        val dialog: StandardDialog = editor.dialog
        // dialog.addClosingTask { updateChartSettings() }
        dialog.isModal = true
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }

val DataViewerTable.insertColumnAction
    get() = createAction(
        "menu_icons/AddTableColumn.png",
        "Insert column",
        "Insert column to the right of selected column, or as the left-most column if none is selected."
    ) {
        insertColumn()
    }

val DataViewerTable.deleteColumnAction
    get() = createAction(
        "menu_icons/DeleteColumnTable.png",
        "Delete columns",
        "Delete selected columns"
    ) {
        deleteSelectedColumns()
    }

val DataViewerTable.insertRowAction
    get() = createAction(
        "menu_icons/AddTableRow.png",
        "Insert row",
        "Insert row to above the selected row, or as the bottom row if none is selected."
    ) {
        insertRow()
    }

val DataViewerTable.deleteRowAction
    get() = createAction(
        "menu_icons/DeleteRowTable.png",
        "Delete rows",
        "Delete selected rows"
    ) {
        deleteSelectedRows()
    }


val DataViewerTable.showHistogramAction
    get() = createAction(
        "menu_icons/histogram.png",
        "Histogram",
        "Create histograms for data in selected column"
    ) {
        GlobalScope.launch(context = Dispatchers.Default) {
            try {
                val canvas = Histogram.of(model.getDoubleColumn(selectedColumn)).canvas();
                canvas.window()
            } catch(e: Exception){
            }
        }
    }

val DataViewerTable.showBoxPlotAction
    get() = createAction(
        "menu_icons/BarChart.png", // TODO Better Icon
        "Boxplot column",
        "Create boxplot for data all numeric columns"
    ) {
        GlobalScope.launch(context = Dispatchers.Default) {
            val canvas = BoxPlot.of(*model.getColumnMajorArray()).canvas();
            canvas.window()
        }
    }


// TODO: Make this usable outside of DataFrameWrapper
// Maybe be possible to adapt that code to a more generic context
val DataViewerTable.showScatterPlotAction
    get() = createAction(
        "menu_icons/ScatterIcon.png",
        "Scatter Plots",
        "Show all pairwise scatter plots across columns"
    ) {
        GlobalScope.launch(context = Dispatchers.Default) {
            // TODO: User should be able to set which column is class
            // TODO: Set mark
            if (model is DataFrameWrapper) {
                val canvas = PlotGrid.splom(model.df, '.', "V1")
                canvas.window()
            }
        }
    }

val DataViewerTable.importArff
    get() = createAction(
        "menu_icons/Import.png",
        "Import arff file...",
        "Import WEKA arff file"
    ) {
        val chooser = SFileChooser(TABLE_DIRECTORY, "", "arff")
        val arffFile = chooser.showOpenDialog()
        if (arffFile != null) {
            model.let {
                if (it is DataFrameWrapper) {
                    it.df = Read.arff(arffFile.absolutePath)
                    it.fireTableStructureChanged()
                } else if (it is BasicDataWrapper) {
                    val df = Read.arff(arffFile.absolutePath)
                    val columns = df.names().zip(df.types())
                        .map { (name, type) -> Column(name, type.getColumnDataType()) }.toMutableList()
                    val dfData = (0 until df.nrows()).map { i ->
                        (0 until df.ncols()).map { j ->
                            df[i][j]
                        }.toMutableList()
                    }.toMutableList()
                    it.data = dfData
                    it.columns = columns
                    it.fireTableStructureChanged()
                }
            }
        }
    }

val DataViewerTable.importCsv
    get() = importCSVAction()

fun DataViewerTable.importCSVAction(fixedColumns: Boolean = false) = createAction(
    "menu_icons/Import.png",
    "Import csv...",
    "Import comma separated values file"
) {
    val chooser = SFileChooser(TABLE_DIRECTORY, "", "csv")
    val csvFile = chooser.showOpenDialog()
    fun checkColumns(numColumns: Int): Boolean {
        if (numColumns != model.columnCount) {
            JOptionPane.showOptionDialog(
                null,
                "Trying to import a table with the wrong number of columns ",
                "Warning",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, null, null)
            return false
        }
        return true
    }
    if (csvFile != null) {
        model.let {
            if (it is BasicDataWrapper) {
                val importedData = createFrom2DArray(Utils.getStringMatrix(csvFile))
                if (checkColumns(importedData.columnCount)) {
                    it.data = importedData.data
                    it.fireTableStructureChanged()
                }
            } else if (it is DataFrameWrapper) {
                val data = Read.csv(csvFile.absolutePath)
                if (checkColumns(data.ncols())) {
                    it.df = data
                    it.fireTableStructureChanged()
                }
            }
        }
    }
}

val DataViewerTable.editColumnAction
    get() = createAction(
        "menu_icons/Prefs.png",
        "Edit column...",
        "Edit column properties"
    ) {
        if (model is BasicDataWrapper) {
            if (selectedColumn >= 0) {
                val editor = AnnotatedPropertyEditor(model.columns[selectedColumn])
                val dialog: StandardDialog = editor.dialog
                dialog.addClosingTask { model.fireTableStructureChanged() }
                // TODO: Add access to histogram etc. from here?
                dialog.isModal = true
                dialog.pack()
                dialog.setLocationRelativeTo(null)
                dialog.isVisible = true
            }
        }
    }
