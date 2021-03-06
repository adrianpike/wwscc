/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dataentry;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.DefaultListSelectionModel;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.LineBorder;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.wwscc.dialogs.TextRunsDialog;
import org.wwscc.storage.Database;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.Run;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;



/**
 * Table used for DataEntry 
 */
public class EntryTable extends JTable implements MessageListener, ActionListener
{
	ListSelectionModel colSel;
	ListSelectionModel rowSel;

	EntryModel model;
	String activeSearch;
	
	public EntryTable(EntryModel m)
	{
		super(m);

		model = m;
		activeSearch = "";

		colSel = new LimitedColSelectionModel();
		rowSel = getSelectionModel();

		/* Selection and DnD/cut/paste */
		getColumnModel().setSelectionModel(colSel);
		setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		setCellSelectionEnabled(true);
		setDragEnabled(true);
		setDropMode(DropMode.INSERT);
		setTransferHandler(new EntryTableTransferHandler());

		/* Drawing and other misc drawing stuff */
		getTableHeader().setReorderingAllowed(false);
		setDefaultRenderer(Run.class, new TimeRenderer());
		setDefaultRenderer(Entrant.class, new EntrantRenderer());
		setRowHeight(36);
		
		
		InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "cut"); // delete is same as Ctl+X
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter Time");

		registerKeyboardAction(
			this,
			"Enter Time",
			KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
		);
		
		addMouseListener(new DClickWatch());
		
		Messenger.register(MT.TIME_ENTERED, this);
		Messenger.register(MT.CAR_ADD, this);
		Messenger.register(MT.CAR_CHANGE, this);
		Messenger.register(MT.COURSE_CHANGED, this);
		Messenger.register(MT.FIND_ENTRANT, this);
	}


	class DClickWatch extends MouseAdapter implements ActionListener
	{
		JPopupMenu driverPopup;
		JPopupMenu runPopup;
		Entrant selectedE;

		public DClickWatch()
		{
			driverPopup = new JPopupMenu("");
			driverPopup.add(createItem("Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK)));
			driverPopup.add(createItem("Add Text Runs", null));
			selectedE = null;
			//copy = createItem("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
			//paste = createItem("Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
		}

		protected JMenuItem createItem(String title, KeyStroke ks)
		{
			JMenuItem item = new JMenuItem(title);
			item.addActionListener(this);
			if (item != null) item.setAccelerator(ks);
			return item;
		}

		protected Object getObject()
		{
			int row = rowSel.getMinSelectionIndex();
			int col = colSel.getMinSelectionIndex();
			return getValueAt(row, col);
		}

		public void doPopup(MouseEvent e)
		{
			int row = rowAtPoint(e.getPoint());
			int col = columnAtPoint(e.getPoint());
			if ((row == -1) || (col == -1)) return;
			if (!rowSel.isSelectedIndex(row)) return;
			if (!colSel.isSelectedIndex(col)) return;
			
			Object sel = getValueAt(row, col);
			if (sel instanceof Entrant)
			{
				selectedE = (Entrant)sel;
				driverPopup.show(EntryTable.this, e.getX(), e.getY());
			}
		}
		
		@Override
		public void mousePressed(MouseEvent e)
		{
			Messenger.sendEvent(MT.OBJECT_CLICKED, getObject());
			if (e.isPopupTrigger())
				doPopup(e);
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			if (e.isPopupTrigger())
				doPopup(e);
		}

		@Override
		public void mouseClicked(MouseEvent e)
		{
			if (e.getClickCount() == 2)
				Messenger.sendEvent(MT.OBJECT_DCLICKED, getObject());
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			String cmd = e.getActionCommand();
			if (cmd.equals("Add Text Runs"))
			{
				TextRunsDialog trd = new TextRunsDialog();
				trd.doDialog("Textual Run Input", null);
				List<Run> runs = trd.getResult();
				if (runs == null) return;
				Map<Integer, Run> course1, course2;
				course1 = new HashMap<Integer,Run>();
				course2 = new HashMap<Integer,Run>();

				Database.d.setCurrentCourse(1);
				Entrant ent = Database.d.loadEntrant(selectedE.getCarId(), false);
				for (Run r : trd.getResult())
				{
					r.updateTo(Database.d.getCurrentEvent().getId(), r.course(), r.run(), selectedE.getCarId(), ent.getIndex());
					if (r.course() == 2) course2.put(r.run(), r); else course1.put(r.run(), r);
				}

				ent.setRuns(course1);
				if (course2.size() > 0)
				{
					Database.d.setCurrentCourse(2);
					ent = Database.d.loadEntrant(selectedE.getCarId(), false);
					ent.setRuns(course2);
				}

				Database.d.setCurrentCourse(1);
				Messenger.sendEvent(MT.RUNGROUP_CHANGED, 1);
			}
			else if (cmd.equals("Cut"))
			{
				e.setSource(EntryTable.this); // redirect as cut action on Table
				TransferHandler.getCutAction().actionPerformed(e);
			}
		}
	}


	class ScrollMe implements Runnable
	{
		public int row;
		public int col;
		public ScrollMe(int r, int c) { row = r; col = c; }
		public void run() { scrollRectToVisible(getCellRect(row, col, true)); };
	}

	public void scrollTable(int row, int col)
	{
		SwingUtilities.invokeLater( new ScrollMe(row, col) );
	}

	
	@Override
	public boolean getScrollableTracksViewportWidth() 
	{
		if (getParent() instanceof JViewport)
		{
			return (((JViewport)getParent()).getWidth() > getMinimumSize().width);
		}

		return false;
	}

	public void setColumnWidths(TableColumn col, int min, int pref, int max)
	{
		if (col == null) return;
		col.setMinWidth(min);
		col.setPreferredWidth(pref);
		col.setMaxWidth(max);
	}

	public void setColumnSizes(TableColumnModelEvent e)
	{
		TableColumnModel tcm = (TableColumnModel)e.getSource();
		int cc = tcm.getColumnCount();
		if (cc <= 1) return;
		
		setColumnWidths(tcm.getColumn(0), 40, 60, 75);
		setColumnWidths(tcm.getColumn(1), 80, 250, 400);
		for (int ii = 2; ii < cc; ii++)
		{
			setColumnWidths(tcm.getColumn(ii), 70, 95, 200);
		}
		doLayout();
	}

	@Override
	public void columnAdded(TableColumnModelEvent e)
	{
		setColumnSizes(e);
		super.columnAdded(e);
	}

	@Override
	public void columnRemoved(TableColumnModelEvent e)
	{
		setColumnSizes(e);
		super.columnRemoved(e);
	}


	public void setSelectedRun(Run r) throws IndexOutOfBoundsException
	{
		int row = rowSel.getMinSelectionIndex();
		int col = colSel.getMinSelectionIndex();

		if ((row < 0) || (col < 0))
			throw new IndexOutOfBoundsException("No table cell selected");
		if ((row >= getRowCount()) || (col >= getColumnCount()))
			throw new IndexOutOfBoundsException("Selection outside of table range");
 		if ((col == 0) || (col == 1))
			throw new IndexOutOfBoundsException("Can't enter time on an Entrant");

		setValueAt(r, row, col);

		/* Advanced the selection point, to next driver with an open cell, select that cell */
		int startrow = ++row;
		int rowcount = getRowCount();

		for (int jj = 0; jj < rowcount; jj++)
		{
			int selectedrow = (startrow + jj) % rowcount;

			for (int ii = 1; ii < getColumnCount(); ii++)
			{
				if (getValueAt(selectedrow, ii) == null)
				{
					rowSel.setSelectionInterval(selectedrow, selectedrow);
					colSel.setSelectionInterval(ii, ii);
					scrollTable(selectedrow, ii);
					return;
				}
			}
		}
		
		/* Nowhere to go, clear selection */		
		rowSel.clearSelection();
		colSel.clearSelection();
	} 


	@Override
	public void event(MT type, Object o)
	{
		switch (type)
		{
			case TIME_ENTERED:
				setSelectedRun((Run)o);
				break;

			case CAR_ADD:
				Sounds.playBlocked();
				model.addCar((Integer)o);
				scrollTable(getRowCount(), 0);
				break;

			case CAR_CHANGE:
				int row = rowSel.getMinSelectionIndex();
				if ((row >= 0) && (row < getRowCount()))
					model.replaceCar((Integer)o, row);
				break;
				
			case COURSE_CHANGED:
				JTableHeader h = getTableHeader();
				if (Database.d.getCurrentCourse() > 1)
				{
					h.setForeground(Color.BLUE);
					h.setBorder(new LineBorder(Color.BLUE));
				}
				else
				{
					h.setForeground(Color.BLACK);
					h.setBorder(new LineBorder(Color.GRAY, 1));
				}
				break;

			case FIND_ENTRANT:
				activeSearch = (String)o;
				repaint();
				break;
		}
	}

	
	@Override
	public void actionPerformed(ActionEvent e)
	{
		Messenger.sendEvent(MT.TIME_ENTER_REQUEST, null);
	}
}


/**
 * Cell Renderer for the Run type
 */
class TimeRenderer extends DefaultTableCellRenderer
{
	private Color backgroundSelect;
	private Color backgroundSelectNoFocus;
	private Color backgroundDone;
	private Color backgroundBest;
	private NumberFormat df;

	public TimeRenderer()
	{
		super();
		backgroundSelect = new Color(0, 0, 255);
		backgroundSelectNoFocus = new Color(190,190,255);
		backgroundDone = new Color(200, 200, 200);
		backgroundBest = new Color(255, 190, 80);
	
		setHorizontalAlignment(CENTER);
		df = NumberFormat.getNumberInstance();
		df.setMinimumFractionDigits(3);
		df.setMaximumFractionDigits(3);
	}


	@Override
	public Component getTableCellRendererComponent (JTable t, Object o, boolean isSelected, boolean hasFocus, int row, int column) 
	{
		Component cell = super.getTableCellRendererComponent(t, o, isSelected, hasFocus, row, column);

		if (isSelected && t.hasFocus())
		{
			setBackground(backgroundSelect);
		}
		else if (isSelected)
		{
			setBackground(backgroundSelectNoFocus);
			setBorder(new LineBorder(Color.RED, 2));
		}
		else
		{
			setBackground(Color.WHITE);
		}

		if (o instanceof Run)
		{
			Run r = (Run)o;
			EntryModel m = (EntryModel)t.getModel();

			if (!isSelected)
			{
				if (r.getNetOrder() == 1)
					setBackground(backgroundBest);
				else if (m.rowIsFull(row))
					setBackground(backgroundDone);
			}

			String display = df.format(r.getRaw()) + " (" + r.getCones() + "," + r.getGates() + ")";
			if (!r.isOK())
				display= "<HTML><center>" + r.getStatus() + "<br><FONT size=-2>" + display;

			setText(display);

		}
		else if (o != null)
		{
			setBackground(Color.red); /* This shouldn't happen */
			setText(o.toString());
		}
		else
		{
			setText("");
		}

		return cell;
	}
}


class EntrantRenderer extends JComponent implements TableCellRenderer 
{
	private Color background;
	private Color backgroundSelect;
	private Color backgroundFound;
	private Color backgroundFoundSelect;
	private String topLine;
	private String bottomLine;
	private Font topFont;
	private Font bottomFont;
	
	public EntrantRenderer()
	{
		super();
		background = new Color(240, 240, 240);
		backgroundSelect = new Color(120, 120, 120);
		backgroundFound = new Color(255, 255, 220);
		backgroundFoundSelect = new Color(255, 255, 120);
		topLine = null;
		bottomLine = null;
		
		topFont = new Font(Font.DIALOG, Font.BOLD, 11);
		bottomFont = new Font(Font.DIALOG, Font.PLAIN, 11);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
						boolean isSelected, boolean hasFocus, int row, int column) 
	{
		setBackground((isSelected) ?  backgroundSelect : background);

		if (value instanceof Entrant)
		{
			Entrant e = (Entrant)value;
		 	switch (column)
			{
				case 0:
					topLine = e.getClassCode();
					bottomLine = ""+e.getNumber();
					break;

				case 1:
					topLine = e.getFirstName() + " " + e.getLastName();
					String index = e.getIndexCode();
					if (index.equals(""))
						bottomLine = e.getCarDesc();
					else
						bottomLine = e.getCarDesc() + " ("+index+") ";
					break;

				default:	
					topLine = "What?";
					bottomLine = null;
					break;
			}

			if (matchMe(topLine, bottomLine, ((EntryTable)table).activeSearch))
				setBackground((isSelected) ?  backgroundFoundSelect : backgroundFound);
		}
		else if (value != null)
		{
			setBackground(Color.red);
			topLine = value.toString();
		}
		else
		{
			setBackground(Color.red);
			topLine = "ERROR";
			bottomLine = "No data for this cell";
		}
		return this;
	}

	protected boolean matchMe(String top, String bottom, String search)
	{
		if (search.equals("")) return false;
		for (String p : search.toLowerCase().split("\\s+"))
		{
			if ((!top.toLowerCase().contains(p)) &&
				(!bottom.toLowerCase().contains(p))) return false;
		}
		return true;
	}

	@Override
	public void paint(Graphics g1)
	{
		Graphics2D g = (Graphics2D)g1;

		Dimension size = getSize();
		g.setColor(getBackground());
		g.fillRect(0, 0, size.width, size.height);
		g.setColor(new Color(40,40,40));
		
		FontMetrics tm = g.getFontMetrics(topFont);
		FontMetrics bm = g.getFontMetrics(bottomFont);
		
		if (topLine != null)
		{
			g.setFont(topFont);
			g.drawString(topLine, 5, size.height/2 - 2);
		}
		if (bottomLine != null)
		{
			g.setFont(bottomFont);
			g.drawString(bottomLine, 5, size.height/2 + bm.getHeight() - 2);
		}
	}
	
	// The following methods override the defaults for performance reasons
	@Override
	public void validate() {}
	@Override
	public void revalidate() {}
	@Override
	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
	@Override
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}



/**
 * Selection model that is used for restricting colummn selection.
 */
class LimitedColSelectionModel extends DefaultListSelectionModel
{
	/*
		JTable seems to only call setSelectionInterval when in SINGLE_INTERVAL mode.
		It also calls addSelectionInterval once in a while but only single clicks not
		drags.  We intercept so that the user can't select both col0 and the other cols
		at the same time.
	*/
	@Override
	public void setSelectionInterval(int index0, int index1)
	{
		if ((index0 == 0) && (index1 != 0)) return;
		if ((index1 == 0) && (index0 != 0)) return;
		super.setSelectionInterval(index0, index1);
	}
}



/**
 * Class to enable special DnD handling in our JTable.
 * Basically, this has boiled down to allow only drag movements (insertions)
 * in the driver column and copy/cut/paste in the runs columns
 */
class EntryTableTransferHandler extends TransferHandler
{
	private static Logger log = Logger.getLogger("org.wwscc.dataentry.EntryTableTransferHandler");
	private int[] rowsidx = null;
	private int[] colsidx = null;
	private boolean isCut = false;


	@Override
	public int getSourceActions(JComponent c)
	{
		return COPY_OR_MOVE;
	}


	@Override
	public void exportAsDrag(JComponent comp, InputEvent e, int action)
	{
		log.fine("export as drag");

		/* Intercept call to locate where the pickup cell was (drag started from) */
		Point loc = ((MouseEvent)e).getPoint();
		JTable table = (JTable)comp;
		int pickupCol = table.columnAtPoint(loc);

		isCut = false;

		/* Limit drag to drivers column for now */
		if ((pickupCol == 0) || (pickupCol == 1))
			super.exportAsDrag(comp, e, action);
	}

	@Override
	public void exportToClipboard(JComponent comp, Clipboard cb, int action)
	{
		isCut = true;
		log.fine("export to clipboard");
		super.exportToClipboard(comp, cb, action);
	}

	/******* Export Side *******/

	/* Create data from the selected rows and columns */
	@Override
	protected Transferable createTransferable(JComponent c)
	{
		JTable table = (JTable)c;
		rowsidx = table.getSelectedRows();
		colsidx = table.getSelectedColumns();

		Object store[][] = new Object[rowsidx.length][colsidx.length];
		for (int ii = 0; ii < rowsidx.length; ii++)
			for (int jj = 0; jj < colsidx.length; jj++)
				store[ii][jj] = table.getValueAt(rowsidx[ii], colsidx[jj]);

		return new DataTransfer(store);
	}

	
	@Override
	protected void exportDone(JComponent c, Transferable data, int action)
	{
		if ((colsidx == null) || (rowsidx == null))
			return;
		if ((colsidx.length == 0) || (rowsidx.length == 0))
			return;

		/* MOVE means Drag or cut (use isCut to determine) */
		if ((action == MOVE) && (isCut))
		{
			EntryTable t = (EntryTable)c;
			if (colsidx[0] > 1)
			{
				log.fine("cut run " + rowsidx.length + "," + colsidx.length);
				for (int ii = 0; ii < rowsidx.length; ii++)
					for (int jj = 0; jj < colsidx.length; jj++)
						t.setValueAt(null, rowsidx[ii], colsidx[jj]);
			}
			else
			{
				log.fine("cut driver");
				for (int ii = 0; ii < rowsidx.length; ii++)
					t.setValueAt(null, rowsidx[ii], 0);
			}
		}

		rowsidx = null;
		colsidx = null;
	}


	/******* Import Side *******/

	/* Called to allow drop operations */
	@Override
	public boolean canImport(TransferHandler.TransferSupport support)
	{
		JTable.DropLocation dl = (JTable.DropLocation)support.getDropLocation();
		JTable target = (JTable)support.getComponent();

		 // allow driver drag full range of rows except for last (Add driver box)
		if (dl.getRow() > target.getRowCount()) return false;  
		int col = dl.getColumn();
		if ((col == 0) || (col == 1)) return true;

		return false;  // but no dragging into runs columns
	}


	/* Called for drop and paste operations */
	@Override
	public boolean importData(TransferHandler.TransferSupport support)
	{
		try
		{
			Object newdata[][] = (Object[][])support.getTransferable().getTransferData(DataTransfer.myFlavor);
			JTable target = (JTable)support.getComponent();
			EntryModel model = (EntryModel)target.getModel();
			int dr,dc;
		

			if (support.isDrop())
			{
				JTable.DropLocation dl = (JTable.DropLocation)support.getDropLocation();
				dr = dl.getRow();
				dc = dl.getColumn();

				if (dc > 1) return false;  // This should never happen, we only drag/drop entrants

				model.moveRow(rowsidx[0], rowsidx[rowsidx.length-1], dr);
				target.clearSelection();
			}
			else // PASTE
			{
				/* Set the data */
				dr = target.getSelectedRow();
				dc = target.getSelectedColumn();

				if (dc <= 1) return false; // We don't paste drivers

				for (int ii = 0; ii < newdata.length; ii++)
					for (int jj = 0; jj < newdata[0].length; jj++)
						model.setValueAt(((Run)newdata[ii][jj]).clone(), dr+ii, dc+jj);
			}

			return true;
		}
		catch (UnsupportedFlavorException ufe) { log.warning("Sorry, you pasted data I don't work with"); }
		catch (IOException ioe) { log.warning("I/O Error during paste:" + ioe); }
		catch (Exception e) { log.warning("General error during paste:" + e); }

		return false;
	}
}



/**
 * Class used for data transfer during Drag/Drop/Copy
 */
class DataTransfer implements Transferable, ClipboardOwner
{
	Object data[][];
	String string;
	static DataFlavor myFlavor;

	static
	{
		myFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=java.lang.Object", "2DArray");
	}

	public DataTransfer(Object data[][])
	{
		int ii, jj;

		this.data = data;
		this.string = new String();
		for (ii = 0; ii < data.length; ii++)
		{
			for (jj = 0; jj < (data[0].length - 1); jj++)
			{
				this.string += data[ii][jj] + "\t";
			}
			this.string += data[ii][jj] + "\n";
		}
	}

	@Override
	public DataFlavor[] getTransferDataFlavors()
	{
		DataFlavor[] flavors = new DataFlavor[2];
		flavors[0] = myFlavor;
		flavors[1] = DataFlavor.stringFlavor;
		return flavors;
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		return (flavor.equals(myFlavor) || flavor.equals(DataFlavor.stringFlavor));
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
	{
		if (flavor.equals(myFlavor))
			return data;
		return string;
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents)
	{
	}
}


