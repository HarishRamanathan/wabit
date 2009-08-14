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

package ca.sqlpower.wabit.swingui.action;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitUtils;
import ca.sqlpower.wabit.enterprise.client.WabitServerInfo;
import ca.sqlpower.wabit.swingui.NewWorkspaceScreen;
import ca.sqlpower.wabit.swingui.WabitSwingSessionContext;

public class NewServerWorkspaceAction extends AbstractAction {

    private final Component dialogOwner;
    private final WabitServerInfo si;
    private final WabitSwingSessionContext context;

    public NewServerWorkspaceAction(Component dialogOwner, WabitServerInfo si, WabitSwingSessionContext context) {
        super(WabitUtils.serviceInfoSummary(si) + "...");
        this.dialogOwner = dialogOwner;
        this.si = si;
        this.context = context;
    }
    
    public void actionPerformed(ActionEvent e) {
        try {
            WabitSession serverSession = context.createServerSession(si);
            context.registerChildSession(serverSession);
            NewWorkspaceScreen newWorkspace = new NewWorkspaceScreen(context, si);
            newWorkspace.showFrame();
        } catch (Exception ex) {
            SPSUtils.showExceptionDialogNoReport(dialogOwner, "Couldn't create new workspace on server", ex);
        }
    }
}