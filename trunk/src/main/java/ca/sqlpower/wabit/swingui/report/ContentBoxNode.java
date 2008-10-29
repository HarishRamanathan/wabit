/*
 * Copyright (c) 2008, SQL Power Group Inc.
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

import org.apache.log4j.Logger;

import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.report.ContentBox;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.util.PPaintContext;

public class ContentBoxNode extends PNode {

    private static final Logger logger = Logger.getLogger(ContentBoxNode.class);
    
    private final ContentBox contentBox;

    private Color borderColour = Color.BLACK;
    private BasicStroke borderStroke = new BasicStroke(1f);

    public ContentBoxNode(ContentBox contentBox) {
        logger.debug("Creating new contentboxnode for " + contentBox);
        this.contentBox = contentBox;
    }
    
    @Override
    public boolean setBounds(double x, double y, double width, double height) {
        boolean boundsSet = super.setBounds(x, y, width, height);
        if (boundsSet) {
            contentBox.setX((int) x);
            contentBox.setY((int) y);
            contentBox.setWidth((int) width);
            contentBox.setHeight((int) height);
        }
        return boundsSet;
    }
    
    @Override
    protected void paint(PPaintContext paintContext) {
        super.paint(paintContext);
        PCamera camera = paintContext.getCamera();
        Graphics2D g2 = paintContext.getGraphics();
        
        g2.setColor(borderColour);
        g2.setStroke(SPSUtils.getAdjustedStroke(borderStroke, camera.getViewScale()));
        g2.draw(getBounds());
    }
}
