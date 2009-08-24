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

package ca.sqlpower.wabit.swingui.olap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.undo.UndoManager;

import net.miginfocom.swing.MigLayout;

import org.apache.log4j.Logger;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.query.Query;

import ca.sqlpower.sql.DatabaseListChangeEvent;
import ca.sqlpower.sql.DatabaseListChangeListener;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.swingui.MultiDragTreeUI;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.query.Messages;
import ca.sqlpower.wabit.olap.OlapQuery;
import ca.sqlpower.wabit.olap.OlapQueryEvent;
import ca.sqlpower.wabit.olap.OlapQueryListener;
import ca.sqlpower.wabit.swingui.QueryPanel;
import ca.sqlpower.wabit.swingui.WabitIcons;
import ca.sqlpower.wabit.swingui.WabitPanel;
import ca.sqlpower.wabit.swingui.WabitSwingSession;
import ca.sqlpower.wabit.swingui.WabitSwingSessionContext;
import ca.sqlpower.wabit.swingui.WabitSwingSessionContextImpl;
import ca.sqlpower.wabit.swingui.action.CreateLayoutFromQueryAction;
import ca.sqlpower.wabit.swingui.action.ExportWabitObjectAction;

public class OlapQueryPanel implements WabitPanel {
    
    private static final Logger logger = Logger.getLogger(OlapQueryPanel.class);
    
    /**
     * This class is the cube trees drag gesture listener, it starts the drag
     * process when necessary.
     */
    private static class CubeTreeDragGestureListener implements DragGestureListener {
        public void dragGestureRecognized(DragGestureEvent dge) {
            dge.getSourceAsDragGestureRecognizer().setSourceActions(DnDConstants.ACTION_COPY);
            JTree t = (JTree) dge.getComponent();
            List<Object> selectedNodes = new ArrayList<Object>();
            for (TreePath path : t.getSelectionPaths()) {
            	selectedNodes.add(path.getLastPathComponent());
            }
            dge.getDragSource().startDrag(dge, null, 
                    new OlapMetadataTransferable(selectedNodes.toArray()), 
                    new DragSourceAdapter() {//just need a default adapter
                    }
            );
        }
    }
    
    /**
     * This is the view component that shows what's in the query.
     */
    private CellSetViewer cellSetViewer;
    
    /**
     * The parent component to the query panel. Message dialogs will be parented
     * to this component or the component's ancestor window.
     */
    private final JComponent parentComponent;

    /**
     * The model that stores values displayed by this panel.
     */
    private final OlapQuery query;
    
    /**
     * This is the JComponent that emcompasses the entire view.
     */
    private JSplitPane queryAndResultsPanel = null;

    private static final Object UNDO_MDX_EDIT = "Undo MDX Edit";

    private static final Object REDO_MDX_EDIT = "Redo MDX Edit";

	private WabitSwingSession session;
	
	/**
	 * Keeps a link to the text control
	 */
	private RSyntaxTextArea mdxTextArea;

	/**
	 * This undo manager is attached to the {@link #mdxTextArea} to allow users to
	 * undo and redo changes to a typed query.
	 */
    private final UndoManager undoManager;
    
    /**
     * This action handles the undo of text editing on the {@link #mdxTextArea}
     */
    private final  Action undoMdxStatementAction = new AbstractAction(Messages.getString("SQLQuery.undo")){

        public void actionPerformed(ActionEvent arg0) {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
            
        }
    };
    
    /**
     * This action handles the redo of text editing on the {@link #mdxTextArea}
     */
    private final Action redoMdxStatementAction = new AbstractAction(Messages.getString("SQLQuery.redo")){

        public void actionPerformed(ActionEvent arg0) {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
            
        }
    };

    /**
     * This tabbed pane has one tab for the drag and drop style query builder
     * and another tab for the text editor.
     */
    private JTabbedPane queryPanels;
    
    /**
     * This tree is the drag source tree that can have parts of a cube dragged from
     * it and dropped on an editor.
     */
    private final JTree cubeTree;
    
    /**
     * This listener is attached to the underlying query being displayed by this
     * panel. This will update the panel when changes occur in the query.
     */
    private final OlapQueryListener queryListener = new OlapQueryListener() {
        public void queryExecuted(final OlapQueryEvent e) {
            SPSUtils.runOnSwingThread(new Runnable() {
                public void run() {
                    updateCellSet(e.getCellSet());
                }
            });
        }
    };
    
    /**
     * This is the {@link JButton} which will reset the Olap4j {@link Query} in the table below.
     * This will allow a user to start their query over without going through the painful and
     * slow steps required to remove each hierarchy. Additionally if the user somehow gets their
     * query into a broken state they can just easily restart.
     */
    private final JButton resetQueryButton;
    
    /**
     * A {@link JComboBox} containing a list of OLAP data sources to choose from
     * to use for the OLAP query
     */
    private JComboBox databaseComboBox;
    
    /**
     * This recreates the database combo box when the list of databases changes.
     */
    private DatabaseListChangeListener dbListChangeListener = new DatabaseListChangeListener() {

        public void databaseAdded(DatabaseListChangeEvent e) {
            if (!(e.getDataSource() instanceof Olap4jDataSource)) return;
            logger.debug("dataBase added");
            databaseComboBox.addItem(e.getDataSource());
            databaseComboBox.revalidate();
        }

        public void databaseRemoved(DatabaseListChangeEvent e) {
            if (!(e.getDataSource() instanceof Olap4jDataSource)) return;
            logger.debug("dataBase removed");
            if (databaseComboBox.getSelectedItem() != null && databaseComboBox.getSelectedItem().equals(e.getDataSource())) {
                databaseComboBox.setSelectedItem(null);
            }
            
            databaseComboBox.removeItem(e.getDataSource());
            databaseComboBox.revalidate();
        }
        
    };
    
    /**
     * This panel contains the cube drag tree as well as a way to choose
     * a connection and cube to drag from.
     */
    private final JPanel dragTreePanel;
    
    
    
    /**
     * This toolbar is placed at the top of the olap query editor.
     */
    private JToolBar olapPanelToolbar;
    
    public OlapQueryPanel(final WabitSwingSession session, final JComponent parentComponent, final OlapQuery query) {
        this.parentComponent = parentComponent;
        this.query = query;
        this.session = session;
        final JFrame parentFrame = ((WabitSwingSessionContext) session.getContext()).getFrame();
        cellSetViewer = new CellSetViewer(session, query);
        
        this.undoManager  = new UndoManager();
        cubeTree = new JTree();
        cubeTree.setRootVisible(false);
        cubeTree.setCellRenderer(new Olap4JTreeCellRenderer());
        cubeTree.setUI(new MultiDragTreeUI());
        cubeTree.updateUI(); //This seems to fix some L&F problems that linux has with the BasicTreeUI
        cubeTree.setBackground(Color.WHITE);
        DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(cubeTree, DnDConstants.ACTION_COPY, new CubeTreeDragGestureListener());

        // inits cubetree state
        try {
            setCurrentCube(query.getCurrentCube());
        } catch (SQLException e2) {
            JOptionPane.showMessageDialog(parentFrame, "The cube in the query " + query.getName() + " could not be accessed from the connection " + query.getOlapDataSource().getName(), "Cannot access cube", JOptionPane.WARNING_MESSAGE);
        } 
        
        resetQueryButton = new JButton();
        resetQueryButton.setIcon(new ImageIcon(OlapQueryPanel.class.getClassLoader().getResource("icons/32x32/cancel.png")));
        resetQueryButton.setToolTipText("Reset Query");
        resetQueryButton.setText("Reset");
        resetQueryButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        resetQueryButton.setHorizontalTextPosition(SwingConstants.CENTER);
        resetQueryButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    query.reset();
                } catch (SQLException e1) {
                    throw new RuntimeException(e1);
                }
            }
        });
        // Removes button borders on OS X 10.5
        resetQueryButton.putClientProperty("JButton.buttonType", "toolbar");
        
        databaseComboBox = new JComboBox(session.getWorkspace().getConnections(Olap4jDataSource.class).toArray());
        databaseComboBox.setSelectedItem(query.getOlapDataSource());
        databaseComboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                Object item = e.getItem();
                if (item instanceof Olap4jDataSource) {
                    query.setOlapDataSource((Olap4jDataSource) item);
                    try {
                        setCurrentCube(null);
                    } catch (SQLException ex) {
                        throw new RuntimeException(
                                "SQL exception occured while trying to set the current cube to null",
                                ex);
                    }
                }
            }
        });
        
        final JButton cubeChooserButton = new JButton("Choose Cube...");
        cubeChooserButton.addActionListener(new AbstractAction() {
        
            public void actionPerformed(ActionEvent e) {
                if (databaseComboBox.getSelectedItem() == null) {
                    JOptionPane.showMessageDialog(parentComponent, 
                            "Please choose a database from the above list first", 
                            "Choose a database", 
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                try {
                    cubeChooserButton.setEnabled(false);
                    JTree tree;
                    try {
                        tree = new JTree(
                                new Olap4jTreeModel(
                                        Collections.singletonList(query.createOlapConnection()),
                                        Cube.class,
                                        Dimension.class));
                    } catch (Exception e1) {
                        throw new RuntimeException(e1);
                    }
                    tree.setCellRenderer(new Olap4JTreeCellRenderer());
                    int row = 0;
                    while (row < tree.getRowCount()) {
                        tree.expandRow(row);
                        row++;
                    }
                    
                    popupChooseCube(parentFrame, cubeChooserButton, tree);
                } finally {
                    cubeChooserButton.setEnabled(true);
                }        
            }
        });
        
        session.getWorkspace().addDatabaseListChangeListener(dbListChangeListener);
        
        dragTreePanel = new JPanel(new MigLayout(
                "fill",
                "[fill,grow 1]",
                "[ | | grow,fill ]"));
        dragTreePanel.add(databaseComboBox, "wrap");
        dragTreePanel.add(cubeChooserButton, "grow 0,left,wrap");
        dragTreePanel.add(new JScrollPane(cubeTree), "spany, wrap");
        
        query.addOlapQueryListener(queryListener);
        
        buildUI();
    }

    private void buildUI() {
    	JComponent textQueryPanel;
        try {
            textQueryPanel = createTextQueryPanel();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        queryPanels = new JTabbedPane();
        olapPanelToolbar = new JToolBar(JToolBar.HORIZONTAL);
        olapPanelToolbar.setFloatable(false);
        JButton executeButton = new JButton(new AbstractAction("Execute", WabitIcons.RUN_ICON_32) {
			public void actionPerformed(ActionEvent e) {
				OlapGuiUtil.asyncExecute(query, session);
			}
		});
        setupButton(executeButton);
        olapPanelToolbar.add(executeButton);
        olapPanelToolbar.add(resetQueryButton);
        olapPanelToolbar.addSeparator();
        
		JButton exportOlapQueryButton = new JButton(
				new ExportWabitObjectAction<OlapQuery>(session, query,
						WabitIcons.EXPORT_ICON_32,
						"Export OLAP Query to Wabit file"));
		exportOlapQueryButton.setText("Export");
		setupButton(exportOlapQueryButton);
		olapPanelToolbar.add(exportOlapQueryButton);
		
        JButton createLayoutButton = new JButton(new CreateLayoutFromQueryAction(session.getWorkspace(), query, query.getName()));
        createLayoutButton.setText("Create Report");
        setupButton(createLayoutButton);
        olapPanelToolbar.add(createLayoutButton);
        
		// TODO: Replace the AbstractAction with a CreateChartFromQueryAction when Charts are first-class objects
		JButton createChartButton = new JButton(new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				// Temporary until Charts are made first-class objects
				JOptionPane.showMessageDialog(OlapQueryPanel.this.getPanel(), "Not Implemented Yet. Sorry!", "Not Implemented", JOptionPane.WARNING_MESSAGE);
			}
		});
		createChartButton.setIcon(new ImageIcon(QueryPanel.class.getClassLoader().getResource("icons/32x32/chart.png")));
		createChartButton.setText("Create Chart");
		setupButton(createChartButton);
		olapPanelToolbar.add(createChartButton);
		
        final JCheckBox nonEmptyRowsCheckbox = new JCheckBox("Omit Empty Rows");
        nonEmptyRowsCheckbox.setSelected(query.isNonEmpty());
        nonEmptyRowsCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    query.setNonEmpty(nonEmptyRowsCheckbox.isSelected());
                    OlapGuiUtil.asyncExecute(query, session);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        olapPanelToolbar.add(nonEmptyRowsCheckbox);
        
        JPanel guiPanel = new JPanel(new BorderLayout());
        
        JToolBar wabitBar = new JToolBar();
		wabitBar.setFloatable(false);
		JButton forumButton = new JButton(WabitSwingSessionContextImpl.FORUM_ACTION);
		forumButton.setBorder(new EmptyBorder(0, 0, 0, 0));
		wabitBar.add(forumButton);
        
        JToolBar toolBar = new JToolBar();
		toolBar.setLayout(new BorderLayout());
		toolBar.add(olapPanelToolbar, BorderLayout.CENTER);
		toolBar.add(wabitBar, BorderLayout.EAST);
        
        guiPanel.add(toolBar, BorderLayout.NORTH);
        JComponent viewComponent = cellSetViewer.getViewComponent();
		guiPanel.add(viewComponent, BorderLayout.CENTER);
		
        queryPanels.add("GUI", guiPanel);
        queryPanels.add("MDX", textQueryPanel);
        
        queryAndResultsPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		queryAndResultsPanel.setLeftComponent(queryPanels);
        queryAndResultsPanel.setRightComponent(dragTreePanel);
        queryAndResultsPanel.setDividerLocation(queryAndResultsPanel.getWidth() - dragTreePanel.getMinimumSize().width);
        queryAndResultsPanel.setResizeWeight(1);
    }
    
    /**
     * Helper method for buildUI. This creates the text editor of
     * the OlapQueryPanel to allow users to type in an MDX query.
     */
    private JComponent createTextQueryPanel() throws OlapException {
        
        // Set basic properties for the mdx window
        this.mdxTextArea = new RSyntaxTextArea();
        this.mdxTextArea.setText("");
        this.mdxTextArea.setLineWrap(true);
        this.mdxTextArea.restoreDefaultSyntaxHighlightingColorScheme();
        this.mdxTextArea.setSyntaxEditingStyle(RSyntaxTextArea.SQL_SYNTAX_STYLE);
        
        // Add support for undo
        this.mdxTextArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });
        this.mdxTextArea.getActionMap().put(UNDO_MDX_EDIT, undoMdxStatementAction);
        this.mdxTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), UNDO_MDX_EDIT);
        
        this.mdxTextArea.getActionMap().put(REDO_MDX_EDIT, redoMdxStatementAction);
        this.mdxTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() + InputEvent.SHIFT_MASK), REDO_MDX_EDIT);
        
        JToolBar toolBar = new JToolBar();
        
        JButton executeButton = new JButton("Execute", WabitIcons.RUN_ICON_32);
        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                CellSet cellSet;
                OlapStatement statement = null;
                OlapConnection connection = null;
                try {
                    connection = query.createOlapConnection();
                    if (connection != null) {
                        try {
                            statement = connection.createStatement();
                            cellSet = statement.executeOlapQuery(mdxTextArea.getText());
                        } catch (OlapException e1) {
                            e1.printStackTrace();
                            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parentComponent), "FAIL\n" + e1.getMessage());
                            return;
                        } finally {
                            if (statement != null) {
                                try {
                                    statement.close();
                                } catch (SQLException e1) {
                                    //squish exception to not hide any exceptions thrown by the catch.
                                }
                            }
                        }

                        cellSetViewer.showCellSet(query, cellSet);
                        queryPanels.setSelectedIndex(0);
                    }
                } catch (Exception e1) {
                    throw new RuntimeException(e1);
                }
            }
        });
        
        setupButton(executeButton);
        toolBar.add(executeButton);
        
        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.add(new JScrollPane(mdxTextArea), BorderLayout.CENTER);
        queryPanel.add(toolBar, BorderLayout.NORTH);

        return queryPanel;
    }
    
    public void maximizeEditor() {
        // TODO Auto-generated method stub

    }

    public boolean applyChanges() {
        cleanup();
        return true;
    }
    
    public void updateMdxText(String mdx) {
        this.mdxTextArea.setText(mdx);
        this.mdxTextArea.repaint();
    }

    public void discardChanges() {
        cleanup();
    }

    /**
     * This method will remove listeners and release resources as required when
     * the panel is being disposed.
     */
    private void cleanup() {
        query.removeOlapQueryListener(queryListener);
        session.getWorkspace().removeDatabaseListChangeListener(dbListChangeListener);
    }

    public JComponent getPanel() {
        if (queryAndResultsPanel == null) {
            buildUI();
        }
        return queryAndResultsPanel;
    }

    public boolean hasUnsavedChanges() {
        return false;
    }
    
    /**
     * Sets the current cube to the given cube. This affects the tree of items
     * that can be dragged into the query builder, and it resets the query
     * builder. It also executes the (empty) query on the new cube.
     * 
     * @param currentCube
     *            The new cube to make current. If this is already the current
     *            cube, the query will not be reset. Can be null to revert to
     *            the "no cube selected" state.
     * @throws SQLException
     */
    public void setCurrentCube(Cube currentCube) throws SQLException {
        if (currentCube != query.getCurrentCube()) {
            query.setCurrentCube(currentCube);
        }
        if (currentCube != null) {
            cubeTree.setModel(new Olap4jTreeModel(Collections.singletonList(currentCube)));
            cubeTree.expandRow(0);
        } else {
            cubeTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Hidden")));
        }
        OlapGuiUtil.asyncExecute(query, session);
    }
    
    /**
     * This method will update the cell set in this panel.
     */
    public void updateCellSet(final CellSet cellSet) {
        cellSetViewer.updateCellSetViewer(query, cellSet);
        try {
            updateMdxText(query.getMdxText());
        } catch (Exception ex) {
            updateMdxText("Exception thrown while retrieving MDX statement:\n" + ex.getMessage());
        }
    }
    
    /**
     * This function will popup the Cube chooser popup from the 'Choose Cube'
     * button. It will popup the window in the bounds of the screen no matter
     * what happens, it will add scrollbars in the right circumstances as well
     * 
     * @param owningFrame
     *      The frame which the popup is being popped up on
     * @param cubeChooserButton
     *      The button which pops up the cube chooser
     * @param tree
     *      The tree in the cube chooser
     */
    private void popupChooseCube(final JFrame owningFrame, final JButton cubeChooserButton, JTree tree) {
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        Point windowLocation = new Point(0, 0);
        SwingUtilities.convertPointToScreen(windowLocation, cubeChooserButton);
        windowLocation.y += cubeChooserButton.getHeight();
        
        Point frameLocation = new Point(0, 0);
        SwingUtilities.convertPointToScreen(frameLocation, owningFrame);
        
        int popupScreenSpaceY = (windowLocation.y - frameLocation.y);
        int maxHeight = (int)(owningFrame.getSize().getHeight() - popupScreenSpaceY);
        
        int width = (int) Math.min(treeScroll.getPreferredSize().getWidth(), owningFrame.getSize().getWidth());
        int height = (int) Math.min(treeScroll.getPreferredSize().getHeight(), maxHeight);
        treeScroll.setPreferredSize(new java.awt.Dimension(width, height));
        
        double popupWidth = treeScroll.getPreferredSize().getWidth();
        int popupScreenSpaceX = (int) (owningFrame.getSize().getWidth() - (windowLocation.x - frameLocation.x));
        int x;
        if (popupWidth > popupScreenSpaceX) {
            x = (int) (windowLocation.x - (popupWidth - popupScreenSpaceX));
        } else {
            x = windowLocation.x;
        }
        
        treeScroll.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.GRAY, Color.GRAY));
        
        JFrame frame = (JFrame)owningFrame;
        final JComponent glassPane; 
        if (frame.getGlassPane() == null) {
            glassPane = new JPanel();
            frame.setGlassPane(glassPane);
        } else {
            glassPane = (JComponent) frame.getGlassPane();
        }
        glassPane.setVisible(true);
        glassPane.setOpaque(false);
        
        PopupFactory pFactory = new PopupFactory();
        final Popup popup = pFactory.getPopup(glassPane, treeScroll, x, windowLocation.y);
        popup.show();
        
        final PopupListenerHandler popupListenerHandler = new PopupListenerHandler(popup, glassPane,owningFrame);
        
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                try {
                    TreePath path = e.getNewLeadSelectionPath();
                    Object node = path.getLastPathComponent();
                    if (node instanceof Cube) {
                        Cube cube = (Cube) node;
                        cubeChooserButton.setEnabled(true);
                        setCurrentCube(cube);
                        popupListenerHandler.cleanup();
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        
    }

    /**
     * This class is a helper class for the
     * {@link OlapQueryPanel#popupChooseCube(JFrame, JButton, JTree)} method.
     * This will add listeners to the popup for clicking on the glass pane and
     * resizing the owning frame. If the popup passed in is hidden in other
     * places the cleanup method should be called.<br>
     * TODO Refactor the cube chooser popup into its own action.
     */
    private static class PopupListenerHandler {
        
        private final MouseAdapter clickListener;
        private final ComponentListener resizeListener;
        private final Popup popup;
        private final JComponent glassPane;
        private final JFrame owningFrame;
        
        public PopupListenerHandler(final Popup popup, final JComponent glassPane, final JFrame owningFrame) {
            this.popup = popup;
            this.glassPane = glassPane;
            this.owningFrame = owningFrame;
            
            clickListener = new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    super.mouseReleased(e);
                    popup.hide();
                    glassPane.removeMouseListener(this);
                    owningFrame.removeComponentListener(resizeListener);
                }
            };

            resizeListener = new ComponentListener() {

                public void componentHidden(ComponentEvent e) {
                    //Do nothing
                }

                public void componentMoved(ComponentEvent e) {
                    popup.hide();
                    owningFrame.removeComponentListener(this);
                    glassPane.removeMouseListener(clickListener);
                }

                public void componentResized(ComponentEvent e) {
                    //Do nothing

                }

                public void componentShown(ComponentEvent e) {
                    //Do nothing
                }

            };
            
            glassPane.addMouseListener(clickListener);
            owningFrame.addComponentListener(resizeListener);
        }
        
        /**
         * This method removes the listeners this class added to the
         * glass pane and the owning frame. This also hides the pop-up.
         */
        public void cleanup() {
            popup.hide();
            owningFrame.removeComponentListener(resizeListener);
            glassPane.removeMouseListener(clickListener);
        }
    }
    
    public String getTitle() {
		return "OLAP Query Editor - " + query.getName();
	}
    
    /**
     * Modifies the button to have their label text at the bottom
     * and centered, and (in OS X 10.5), to have no button border
     */
    private void setupButton(JButton button) {
    	button.setVerticalTextPosition(SwingConstants.BOTTOM);
    	button.setHorizontalTextPosition(SwingConstants.CENTER);
    	button.putClientProperty("JButton.buttonType", "toolbar");
    }
}