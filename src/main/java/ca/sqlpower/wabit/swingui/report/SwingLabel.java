/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui.report;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

import ca.sqlpower.swingui.ColorCellRenderer;
import ca.sqlpower.swingui.DataEntryPanel;
import ca.sqlpower.swingui.FontSelector;
import ca.sqlpower.wabit.report.HorizontalAlignment;
import ca.sqlpower.wabit.report.Label;
import ca.sqlpower.wabit.report.VerticalAlignment;
import ca.sqlpower.wabit.report.ReportContentRenderer.BackgroundColours;
import ca.sqlpower.wabit.swingui.Icons;
import ca.sqlpower.wabit.swingui.InsertVariableButton;
import edu.umd.cs.piccolo.event.PInputEvent;

public class SwingLabel implements SwingContentRenderer {
    
    private static final Logger logger = Logger.getLogger(SwingLabel.class);
    
    private final Label renderer;

    public SwingLabel(Label renderer) {
        this.renderer = renderer;
    }

    public DataEntryPanel getPropertiesPanel() {
        final DefaultFormBuilder fb = new DefaultFormBuilder(new FormLayout("pref, 4dlu, 250dlu:grow"));
        
        final JTextArea textArea = new JTextArea(renderer.getText());
        JButton variableButton = new InsertVariableButton(renderer.getVariableContext(), textArea);
        
        ButtonGroup hAlignmentGroup = new ButtonGroup();
        final JToggleButton leftAlign = new JToggleButton(Icons.LEFT_ALIGN_ICON, 
                renderer.getHorizontalAlignment() == HorizontalAlignment.LEFT);
        hAlignmentGroup.add(leftAlign);
        final JToggleButton centreAlign = new JToggleButton(Icons.CENTRE_ALIGN_ICON, 
                renderer.getHorizontalAlignment() == HorizontalAlignment.CENTER);
        hAlignmentGroup.add(centreAlign);
        final JToggleButton rightAlign = new JToggleButton(Icons.RIGHT_ALIGN_ICON, 
                renderer.getHorizontalAlignment() == HorizontalAlignment.RIGHT);
        hAlignmentGroup.add(rightAlign);

        ButtonGroup vAlignmentGroup = new ButtonGroup();
        final JToggleButton topAlign = new JToggleButton(Icons.TOP_ALIGN_ICON, 
                renderer.getVerticalAlignment() == VerticalAlignment.TOP);
        vAlignmentGroup.add(topAlign);
        final JToggleButton middleAlign = new JToggleButton(Icons.MIDDLE_ALIGN_ICON, 
                renderer.getVerticalAlignment() == VerticalAlignment.MIDDLE);
        vAlignmentGroup.add(middleAlign);
        final JToggleButton bottomAlign = new JToggleButton(Icons.BOTTOM_ALIGN_ICON, 
                renderer.getVerticalAlignment() == VerticalAlignment.BOTTOM);
        vAlignmentGroup.add(bottomAlign);

        Box alignmentBox = Box.createHorizontalBox();
        alignmentBox.add(leftAlign);
        alignmentBox.add(centreAlign);
        alignmentBox.add(rightAlign);
        alignmentBox.add(Box.createHorizontalStrut(5));
        alignmentBox.add(topAlign);
        alignmentBox.add(middleAlign);
        alignmentBox.add(bottomAlign);
        alignmentBox.add(Box.createHorizontalGlue());
        alignmentBox.add(variableButton);
        fb.append("Alignment", alignmentBox);

        fb.appendRelatedComponentsGapRow();
        fb.nextLine();
        
        fb.appendRow("fill:90dlu:grow");
        fb.nextLine();
        textArea.setFont(renderer.getFont());
        JLabel textLabel = fb.append("Text", new JScrollPane(textArea));
        textLabel.setVerticalTextPosition(JLabel.TOP);
        
        fb.nextLine();
        final FontSelector fontSelector = new FontSelector(renderer.getFont());
        logger.debug("FontSelector got passed Font " + renderer.getFont());
        fontSelector.setShowingPreview(false);
        fontSelector.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                logger.debug("Changing font to: " + fontSelector.getSelectedFont());
                textArea.setFont(fontSelector.getSelectedFont());
            }
        });
        fb.append("Font", fontSelector.getPanel());
        
        fb.nextLine();
        final JLabel colourLabel = new JLabel(" ");
        colourLabel.setBackground(renderer.getBackgroundColour());
        colourLabel.setOpaque(true);
        final JComboBox colourCombo = new JComboBox();
        colourCombo.setRenderer(new ColorCellRenderer(85, 30));
        for (BackgroundColours bgColour : BackgroundColours.values()) {
            colourCombo.addItem(bgColour.getColour());
        }
        colourCombo.setSelectedItem(renderer.getBackgroundColour());
        colourCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color colour = (Color) colourCombo.getSelectedItem();
                colourLabel.setBackground(colour);
            }
        });
        JPanel colourPanel = new JPanel(new BorderLayout());
        colourPanel.add(colourLabel, BorderLayout.CENTER);
        colourPanel.add(colourCombo, BorderLayout.EAST);
        fb.append("Background", colourPanel);
        
        DataEntryPanel dep = new DataEntryPanel() {

            public boolean applyChanges() {

                fontSelector.applyChanges();
                renderer.setFont(fontSelector.getSelectedFont());

                renderer.setText(textArea.getText());
                
                if (leftAlign.isSelected()) {
                    renderer.setHorizontalAlignment(HorizontalAlignment.LEFT);
                } else if (centreAlign.isSelected()) {
                    renderer.setHorizontalAlignment(HorizontalAlignment.CENTER);
                } else if (rightAlign.isSelected()) {
                    renderer.setHorizontalAlignment(HorizontalAlignment.RIGHT);
                }

                if (topAlign.isSelected()) {
                    renderer.setVerticalAlignment(VerticalAlignment.TOP);
                } else if (middleAlign.isSelected()) {
                    renderer.setVerticalAlignment(VerticalAlignment.MIDDLE);
                } else if (bottomAlign.isSelected()) {
                    renderer.setVerticalAlignment(VerticalAlignment.BOTTOM);
                }
                
                renderer.setBackgroundColour((Color) colourCombo.getSelectedItem());

                return true;
            }

            public void discardChanges() {
                // no op
            }

            public JComponent getPanel() {
                return fb.getPanel();
            }

            public boolean hasUnsavedChanges() {
                return true;
            }
            
        };
        return dep;
    }

    public void processEvent(PInputEvent event, int type) {
        //do something cool here later
    }

}