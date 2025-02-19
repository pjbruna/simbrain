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
package org.simbrain.network.gui.nodes.subnetworkNodes;

import org.simbrain.network.gui.NetworkPanel;
import org.simbrain.network.gui.dialogs.network.BackpropEditorDialog;
import org.simbrain.network.gui.nodes.SubnetworkNode;
import org.simbrain.network.subnetworks.BackpropNetwork;
import org.simbrain.util.StandardDialog;

import javax.swing.*;

/**
 * PNode representation of a group of a backprop network.
 *
 * @author jyoshimi
 */
public class BackpropNetworkNode extends SubnetworkNode {

    /**
     * Create a layered network.
     *
     * @param networkPanel parent panel
     * @param group        the layered network
     */
    public BackpropNetworkNode(NetworkPanel networkPanel, BackpropNetwork group) {
        super(networkPanel, group);
        setContextMenu();
    }

    /**
     * Sets custom menu.
     */
    private void setContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        editAction.putValue("Name", "Edit / Train Backprop...");
        menu.add(editAction);
        menu.add(renameAction);
        menu.add(removeAction);
        menu.addSeparator();
        BackpropNetwork network = (BackpropNetwork) getSubnetwork();
        JMenu dataActions = new JMenu("View / Edit Data");
        // dataActions.add(TrainerGuiActions.getEditCombinedDataAction(getNetworkPanel(), network));
        // dataActions.addSeparator();
        // dataActions.add(TrainerGuiActions.getEditDataAction(getNetworkPanel(), network.getInputNeurons(), network.getTrainingSet().getInputDataMatrix(), "Input"));
        // dataActions.add(TrainerGuiActions.getEditDataAction(getNetworkPanel(), network.getOutputNeurons(), network.getTrainingSet().getTargetDataMatrix(), "Target"));
        // menu.add(dataActions);
        setContextMenu(menu);
    }

    @Override
    public StandardDialog getPropertyDialog() {
        return new BackpropEditorDialog(getNetworkPanel(), (BackpropNetwork) getSubnetwork());
    }

}