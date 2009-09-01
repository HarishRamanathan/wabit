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

package ca.sqlpower.wabit.swingui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.sqlpower.sqlobject.SQLObjectException;
import ca.sqlpower.swingui.DocumentAppender;
import ca.sqlpower.swingui.MemoryMonitor;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.SPSwingWorker;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.event.SessionLifecycleEvent;
import ca.sqlpower.swingui.event.SessionLifecycleListener;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitProject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitSessionContext;
import ca.sqlpower.wabit.WabitVersion;
import ca.sqlpower.wabit.dao.LoadProjectXMLDAO;
import ca.sqlpower.wabit.dao.ProjectXMLDAO;
import ca.sqlpower.wabit.query.QueryCache;
import ca.sqlpower.wabit.report.Layout;
import ca.sqlpower.wabit.swingui.action.ImportProjectAction;
import ca.sqlpower.wabit.swingui.action.LoadProjectsAction;
import ca.sqlpower.wabit.swingui.action.LogAction;
import ca.sqlpower.wabit.swingui.action.SaveAsProjectAction;
import ca.sqlpower.wabit.swingui.action.SaveProjectAction;
import ca.sqlpower.wabit.swingui.report.ReportLayoutPanel;
import ca.sqlpower.wabit.swingui.tree.ProjectTreeCellEditor;
import ca.sqlpower.wabit.swingui.tree.ProjectTreeCellRenderer;
import ca.sqlpower.wabit.swingui.tree.ProjectTreeModel;


/**
 * The Main Window for the Wabit Application; contains a main() method that is
 * the conventional way to start the application running.
 */
public class WabitSwingSessionImpl implements WabitSwingSession {
	
	/**
	 * A constant for storing the location of the query dividers in prefs.
	 */
	private static final String QUERY_DIVIDER_LOCATON = "QueryDividerLocaton";

	/**
	 * A constant for storing the location of the divider for layouts in prefs.
	 */
	private static final String LAYOUT_DIVIDER_LOCATION = "LayoutDividerLocation";
	
	private static Logger logger = Logger.getLogger(WabitSwingSessionImpl.class);
	
	private class WindowClosingListener extends WindowAdapter {
		
		private final WabitSwingSession session;

		public WindowClosingListener(WabitSwingSession session) {
			this.session = session;
		}
		
		@Override
		public void windowClosing(WindowEvent e) {
	    	session.close();
		}
	}

	private final WabitSessionContext sessionContext;
	
	private final WabitProject project;
	
	private JTree projectTree;
	private JSplitPane wabitPane;
	private final JFrame frame;
	private static JLabel statusLabel;
	
	private static final ImageIcon FRAME_ICON = new ImageIcon(WabitSwingSessionImpl.class.getResource("/icons/wabit-16.png"));

	
	private final Preferences prefs = Preferences.userNodeForPackage(WabitSwingSessionImpl.class);
	
	/**
	 * All information useful to the user in a log format should be logged here.
	 * The user can get access to the contents of this log from the window's menu.
	 */
	private final Logger userInformationLogger = Logger.getLogger("User Info Log");

	/**
	 * The list of all currently-registered background tasks.
	 */
	private final List<SPSwingWorker> activeWorkers =
		Collections.synchronizedList(new ArrayList<SPSwingWorker>());
	
	private final List<SessionLifecycleListener<WabitSession>> lifecycleListeners =
		new ArrayList<SessionLifecycleListener<WabitSession>>();

	/**
	 * This is the current panel to the right of the JTree showing the parts of the 
	 * project. This will allow editing the currently selected element in the JTree.
	 */
	private WabitPanel currentEditorPanel;

	/**
	 * This is the model of the current panel.
	 */
	private Object currentEditorPanelModel;
	
	/**
	 * This DB connection manager will allow editing the db connections in the
	 * pl.ini file. This DB connection manager can be used anywhere needed in 
	 * wabit. 
	 */
	private final DatabaseConnectionManager dbConnectionManager;
	
	/**
	 * This is the most recent file loaded in this session or the last file that the session
	 * was saved to. This will be null if no file has been loaded or the project has not
	 * been saved yet.
	 */
	private File currentFile = null;
	
	/**
	 * Creates a new session 
	 * 
	 * @param context
	 */
	public WabitSwingSessionImpl(WabitSessionContext context) {
	    project = new WabitProject();
		sessionContext = context;
		sessionContext.registerChildSession(this);
		
		statusLabel= new JLabel();
		
		frame = new JFrame("Wabit " + WabitVersion.VERSION);
		frame.setIconImage(FRAME_ICON.getImage());
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowClosingListener(this));
		
		dbConnectionManager = new DatabaseConnectionManager(getContext().getDataSources());
	}
	/**
	 * sets the StatusMessage
	 */
	public static void setStatusMessage (String msg) {
		statusLabel.setText(msg);	
	}
	
	/**
	 *  Builds the GUI
	 * @throws SQLObjectException 
	 */
    public void buildUI() throws SQLObjectException {
        
        // this will be the frame's content pane
		JPanel cp = new JPanel(new BorderLayout());
    	
    	wabitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    	
		projectTree = new JTree(new ProjectTreeModel(project));
		projectTree.addMouseListener(new ProjectTreeListener(this));
    	ProjectTreeCellRenderer renderer = new ProjectTreeCellRenderer();
		projectTree.setCellRenderer(renderer);
    	projectTree.setCellEditor(new ProjectTreeCellEditor(projectTree, renderer));
    	projectTree.setEditable(true);

        wabitPane.add(new JScrollPane(SPSUtils.getBrandedTreePanel(projectTree)), JSplitPane.LEFT);
		setEditorPanel(project);
    	
		//prefs
    	if(prefs.get("MainDividerLocaton", null) != null) {
            String[] dividerLocations = prefs.get("MainDividerLocaton", null).split(",");
            wabitPane.setDividerLocation(Integer.parseInt(dividerLocations[0]));
        }
        
        JPanel statusPane = new JPanel(new BorderLayout());
        statusPane.add(statusLabel, BorderLayout.CENTER);
		
		MemoryMonitor memoryMonitor = new MemoryMonitor();
		memoryMonitor.start();
		JLabel memoryLabel = memoryMonitor.getLabel();
		memoryLabel.setBorder(new EmptyBorder(0, 20, 0, 20));
		statusPane.add(memoryLabel, BorderLayout.EAST);
		
		cp.add(wabitPane, BorderLayout.CENTER);
        cp.add(statusPane, BorderLayout.SOUTH);
        
        JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic('f');
		menuBar.add(fileMenu);
		fileMenu.add(new LoadProjectsAction(this, this.getContext()));
		fileMenu.addSeparator();
		fileMenu.add(new SaveProjectAction(this));
		fileMenu.add(new SaveAsProjectAction(this));
		fileMenu.addSeparator();
		fileMenu.add(new ImportProjectAction(this));
		
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic('v');
		menuBar.add(viewMenu);
		JMenuItem maxEditor = new JMenuItem(new AbstractAction("Maximize Editor") {
			public void actionPerformed(ActionEvent e) {
				if (currentEditorPanel != null) {
					currentEditorPanel.maximizeEditor();
				}
			}
		});
		maxEditor.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK));
		viewMenu.add(maxEditor);
        
		JMenu windowMenu = new JMenu("Window");
		windowMenu.setMnemonic('w');
		menuBar.add(windowMenu);
		JTextArea logTextArea = new JTextArea();
		DocumentAppender docAppender = new DocumentAppender(logTextArea.getDocument());
		userInformationLogger.addAppender(docAppender);
		JMenuItem logMenuItem = new JMenuItem(new LogAction(frame, logTextArea ));
		windowMenu.add(logMenuItem);
		
		frame.setJMenuBar(menuBar);
        frame.setContentPane(cp);
        
        //prefs
        if (prefs.get("frameBounds", null) != null) {
            String[] frameBounds = prefs.get("frameBounds", null).split(",");
            if (frameBounds.length == 4) {
            	logger.debug("Frame bounds are " + Integer.parseInt(frameBounds[0]) + ", " + Integer.parseInt(frameBounds[1]) + ", " +
                        Integer.parseInt(frameBounds[2]) + ", " + Integer.parseInt(frameBounds[3]));
                frame.setBounds(
                        Integer.parseInt(frameBounds[0]),
                        Integer.parseInt(frameBounds[1]),
                        Integer.parseInt(frameBounds[2]),
                        Integer.parseInt(frameBounds[3]));
            }
        } else {
        	frame.setSize(1050, 750);
        	frame.setLocation(200, 100);
        }

        frame.setVisible(true);

        logger.debug("UI is built.");
    }
    
    public JTree getTree() {
    	return projectTree;
    }

    /* docs inherited from interface */
	public void registerSwingWorker(SPSwingWorker worker) {
		activeWorkers.add(worker);
	}

    /* docs inherited from interface */
	public void removeSwingWorker(SPSwingWorker worker) {
		activeWorkers.remove(worker);
	}

	public void addSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.add(l);
	}

	public void removeSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.remove(l);
	}
	
	public void close() {
		if (!removeEditorPanel()) {
    		return;
    	}
		setEditorPanel(project);
		
    	try {
        	prefs.put("MainDividerLocaton", String.format("%d", wabitPane.getDividerLocation()));
            prefs.put("frameBounds", String.format("%d,%d,%d,%d", frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
            prefs.flush();
        } catch (BackingStoreException ex) {
            logger.log(Level.WARN,"Failed to flush preferences", ex);
        }
        
        boolean closing = false;
		if (hasUnsavedChanges()) {
			int response = JOptionPane.showOptionDialog(frame,
					"You have unsaved changes. Do you want to save?", "Unsaved Changes", //$NON-NLS-1$ //$NON-NLS-2$
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[] {"Don't Save", "Cancel", "Save"}, "Save"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            if (response == 0) {
                sessionContext.deregisterChildSession(this);
                frame.dispose();
            } else if (response == JOptionPane.CLOSED_OPTION || response == 1) {
            	return;
            } else {
            	if (new SaveAsProjectAction(this).save()) {
            		closing = true;
            	}
            }
		} else {
			closing = true;
		}
		
		if (closing) {
	    	SessionLifecycleEvent<WabitSession> lifecycleEvent =
	    		new SessionLifecycleEvent<WabitSession>(this);
	    	for (int i = lifecycleListeners.size() - 1; i >= 0; i--) {
	    		lifecycleListeners.get(i).sessionClosing(lifecycleEvent);
	    	}
	    	
	    	sessionContext.deregisterChildSession(this);
    		frame.dispose();
		}
	}

    /**
     * Launches the Wabit application by loading the configuration and
     * displaying the GUI.
     * 
     * @throws Exception if startup fails
     */
    public static void  main(final String[] args) throws Exception {
    	System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Wabit");
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	
    	SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				try {
					WabitSessionContext context = new WabitSwingSessionContextImpl(true);
					
					final File importFile;
					if (args.length > 0) {
						importFile = new File(args[0]);
					} else {
						importFile = null;
					}
					
					WabitSwingSessionImpl wss;
					
					if (importFile != null) {
						BufferedInputStream in = null;
						try {
							in = new BufferedInputStream(new FileInputStream(importFile));
						} catch (FileNotFoundException e1) {
							throw new RuntimeException(e1);
						}
						LoadProjectXMLDAO projectLoader = new LoadProjectXMLDAO(context, in);
						List<WabitSession> sessions = projectLoader.loadProjects();
						for (WabitSession session : sessions) {
							((WabitSwingSession)session).buildUI();
						}
					} else {
						wss = new WabitSwingSessionImpl(context);
						wss.buildUI();
					}
				} catch (Exception ex) {
					 ex.printStackTrace();
					// We wish we had a parent component to direct the dialog but this is being invoked, so
					// everything else blew up.
					SPSUtils.showExceptionDialogNoReport("An unexpected error occured while launching Wabit",ex);
				}
			}
    	});
    	
    }

    public WabitProject getProject() {
        return project;
    }
    
	public WabitSessionContext getContext() {
		return sessionContext;
	}
	
	public Logger getUserInformationLogger() {
		return userInformationLogger;
	}
	
	public JFrame getFrame() {
		return frame;
	}
	
	public void setEditorPanel(Object entryPanelModel) {
		if (entryPanelModel == currentEditorPanelModel) {
			return;
		}
		if (!removeEditorPanel()) {
			return;
		}
		int dividerLoc = wabitPane.getDividerLocation();
		currentEditorPanelModel = entryPanelModel;
		if (entryPanelModel instanceof QueryCache) {
			QueryPanel queryPanel = new QueryPanel(this, (QueryCache)entryPanelModel);
		   	if (prefs.get(QUERY_DIVIDER_LOCATON, null) != null) {
	            String[] dividerLocations = prefs.get(QUERY_DIVIDER_LOCATON, null).split(",");
	            queryPanel.getTopRightSplitPane().setDividerLocation(Integer.parseInt(dividerLocations[0]));
	            queryPanel.getFullSplitPane().setDividerLocation(Integer.parseInt(dividerLocations[1]));
		   	}
		   	currentEditorPanel = queryPanel;
		} else if (entryPanelModel instanceof Layout) {
			ReportLayoutPanel rlPanel = new ReportLayoutPanel(this, (Layout) entryPanelModel);
			if (prefs.get(LAYOUT_DIVIDER_LOCATION, null) != null) {
				rlPanel.getSplitPane().setDividerLocation(Integer.parseInt(prefs.get(LAYOUT_DIVIDER_LOCATION, null)));
			}
			currentEditorPanel = rlPanel;
		} else if (entryPanelModel instanceof WabitProject) {
			currentEditorPanel = new ProjectPanel(this);
		} else {
			if (entryPanelModel instanceof WabitObject && ((WabitObject) entryPanelModel).getParent() != null) {
				setEditorPanel(((WabitObject) entryPanelModel).getParent()); 
			} else {
				throw new IllegalStateException("Unknown model for the defined types of entry panels. The type is " + entryPanelModel.getClass());
			}
		}
		wabitPane.add(currentEditorPanel.getPanel(), JSplitPane.RIGHT);
		wabitPane.setDividerLocation(dividerLoc);
	}
	
	/**
	 * This will close the editor panel the user is currently modifying if 
	 * the user has no changes or discards their changes. This will return true
	 * if the panel was properly closed or false if it was not closed (ie: due to
	 * unsaved changes).
	 */
	private boolean removeEditorPanel() {
		if (currentEditorPanel != null && currentEditorPanel.hasUnsavedChanges()) {
			int retval = JOptionPane.showConfirmDialog(frame, "There are unsaved changes. Discard?", "Discard changes", JOptionPane.YES_NO_OPTION);
			if (retval == JOptionPane.NO_OPTION) {
				return false;
			}
		}
		if (currentEditorPanel != null) {
			if (currentEditorPanel instanceof QueryPanel) {
				QueryPanel query = (QueryPanel)currentEditorPanel;
				prefs.put(QUERY_DIVIDER_LOCATON, String.format("%d,%d", query.getTopRightSplitPane().getDividerLocation(), query.getFullSplitPane().getDividerLocation()));
			} else if (currentEditorPanel instanceof ReportLayoutPanel) {
				prefs.put(LAYOUT_DIVIDER_LOCATION, String.format("%d", ((ReportLayoutPanel) currentEditorPanel).getSplitPane().getDividerLocation()));
			}
			currentEditorPanel.discardChanges();
			wabitPane.remove(currentEditorPanel.getPanel());
		}
		currentEditorPanel = null;
		currentEditorPanelModel = null;
		return true;
	}
	
	public DatabaseConnectionManager getDbConnectionManager() {
		return dbConnectionManager;
	}
	
	private boolean hasUnsavedChanges() {
		if (currentFile == null) {
			return project.getChildren().size() > 0;
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new ProjectXMLDAO(out, project).save();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(currentFile));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		char[] buffer = new char[out.toString().toCharArray().length];
		
		try {
			reader.read(buffer, 0, out.toString().toCharArray().length);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		for (int i = 0; i < out.toString().toCharArray().length && i < buffer.length; i++) {
			if (out.toString().toCharArray()[i] != buffer[i]) {
				logger.debug("Difference at position " + i + " character " + out.toString().toCharArray()[i] + " " + buffer[i]);
				return true;
			}
		}
		
		return false;
	}
	
	public void setCurrentFile(File savedOrLoadedFile) {
		this.currentFile = savedOrLoadedFile;
	}
	
	public File getCurrentFile() {
		return currentFile;
	}
	
}