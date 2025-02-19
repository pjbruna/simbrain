/*
 * Part of Simbrain--a java-based neural network kit
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.world.textworld.gui

import org.simbrain.util.genericframe.GenericFrame
import org.simbrain.util.widgets.ShowHelpAction
import org.simbrain.workspace.component_actions.CloseAction
import org.simbrain.workspace.component_actions.OpenAction
import org.simbrain.workspace.component_actions.SaveAction
import org.simbrain.workspace.component_actions.SaveAsAction
import org.simbrain.workspace.gui.DesktopComponent
import org.simbrain.world.textworld.TextWorld
import org.simbrain.world.textworld.TextWorldComponent
import org.simbrain.world.textworld.loadText
import org.simbrain.world.textworld.textWorldPrefs
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JToolBar

/**
 * **ReaderComponentDesktopGui** is the gui view for the reader world.
 */
class TextWorldDesktopComponent(frame: GenericFrame, component: TextWorldComponent) :
    DesktopComponent<TextWorldComponent?>(frame, component) {
    /**
     * Menu Bar.
     */
    private val menuBar = JMenuBar()

    /**
     * File menu for saving and opening world files.
     */
    private val file = JMenu("File")

    /**
     * Edit menu Item.
     */
    private val edit = JMenu("Edit")

    /**
     * Opens the dialog to define TextWorld Dictionary.
     */
    private val loadDictionary = JMenuItem("Edit dictionary...")

    /**
     * Opens user preferences dialog.
     */
    private val preferences = JMenuItem("Preferences")

    /**
     * Opens the help dialog for TextWorld.
     */
    private val help = JMenu("Help")

    /**
     * Help menu item.
     */
    private val helpItem = JMenuItem("Reader Help")

    /**
     * The pane representing the text world.
     */
    private val panel: TextWorldPanel

    /**
     * The text world.
     */
    private val world: TextWorld

    /**
     * Creates a new frame of type TextWorld.
     *
     * @param frame
     * @param component
     */
    init {
        world = component.world
        val openSaveToolBar = JToolBar()
        openSaveToolBar.add(OpenAction(this))
        openSaveToolBar.add(SaveAction(this))
        panel = TextWorldPanel.createReaderPanel(world, openSaveToolBar)
        this.preferredSize = Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        addMenuBar()
        add(panel)
        frame.pack()

        // Force component to fill up parent panel
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val component = e.component
                panel.preferredSize = Dimension(component.width, component.height)
                panel.revalidate()
            }
        })
        parentFrame.pack()
    }

    /**
     * Adds menu bar to the top of TextWorldComponent.
     */
    private fun addMenuBar() {

        // File Menu
        menuBar.add(file)
        file.add(OpenAction(this))
        file.add(SaveAction(this))
        file.add(SaveAsAction(this))
        file.addSeparator()
        file.add(world.loadText)
        file.addSeparator()
        file.add(CloseAction(workspaceComponent))

        // Edit menu
        // loadDictionary.setAction(TextWorldActions.showDictionaryEditor(world));
        // edit.add(loadDictionary);
        // edit.addSeparator()
        preferences.action = world.textWorldPrefs
        edit.add(preferences)
        menuBar.add(edit)

        // Help Menu
        menuBar.add(help)
        val helpAction = ShowHelpAction("Pages/Worlds/TextWorld/TextWorld.html")
        helpItem.action = helpAction
        help.add(helpItem)

        // Add menu
        parentFrame.jMenuBar = menuBar
    }

    companion object {
        /**
         * Default height.
         */
        private const val DEFAULT_HEIGHT = 250

        /**
         * Default width.
         */
        private const val DEFAULT_WIDTH = 400
    }
}