/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2010 Brett Wilson.
 * All rights reserved.
 */


package org.wwscc.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;
import org.wwscc.dialogs.BaseDialog.DialogFinisher;
import org.wwscc.dialogs.CarDialog;
import org.wwscc.dialogs.DriverDialog;
import org.wwscc.storage.Car;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;
import org.wwscc.util.SearchTrigger;


public abstract class DriverCarPanel extends JPanel implements ActionListener, ListSelectionListener, FocusListener, MessageListener
{
	private static Logger log = Logger.getLogger(DriverCarPanel.class.getCanonicalName());

	protected JTextField firstSearch;
	protected JTextField lastSearch;
	protected SearchDrivers searchDrivers = new SearchDrivers();

	protected JScrollPane dscroll;
	protected JList drivers;
	protected JTextArea driverInfo;

	protected JScrollPane cscroll;
	protected JList cars;
	protected JTextArea carInfo;

	protected boolean carAddOption = false;
	protected Driver selectedDriver;
	protected Car selectedCar;

	public DriverCarPanel()
	{
		super();
		setLayout(new MigLayout("", "fill"));

		selectedDriver = null;
		selectedCar = null;

		/* Search Section */
		firstSearch = new JTextField("", 8);
		firstSearch.getDocument().addDocumentListener(searchDrivers);
		firstSearch.addFocusListener(this);
		lastSearch = new JTextField("", 8);
		lastSearch.getDocument().addDocumentListener(searchDrivers);
		lastSearch.addFocusListener(this);

		/* Driver Section */
		drivers = new JList();
		drivers.addListSelectionListener(this);
		drivers.setVisibleRowCount(1);
		drivers.setPrototypeCellValue("12345678901234567890");
		drivers.setSelectionMode(0);

		dscroll = new JScrollPane(drivers);
		dscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		dscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		dscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));

		driverInfo = displayArea("\n\n\n\n");

		/* Car Section */
		cars = new JList();
		cars.addListSelectionListener(this);
		cars.setVisibleRowCount(2);
		cars.setPrototypeCellValue("12345678901234567890");
		cars.setSelectionMode(0);
	
		cscroll = new JScrollPane(cars);
		cscroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		cscroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		cscroll.getVerticalScrollBar().setPreferredSize(new Dimension(15,200));

		carInfo = displayArea("\n");
	}


	protected JTextArea displayArea(String text)
	{
		JTextArea ta = new JTextArea(text);
		ta.setEditable(false);
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setBackground((Color)UIManager.get("Label.background"));
		ta.setForeground(new Color(20, 20, 150));
		ta.setFont(new Font("Dialog", Font.PLAIN, 12));

		return ta;
	}


	/**
	 * Set the name search fields and select the name.
	 * @param firstname the value to put in the firstname field
	 * @param lastname  the value to put in the lastname field
	 */
	public void focusOnDriver(String firstname, String lastname)
	{
		firstSearch.setText(firstname);
		lastSearch.setText(lastname);
		drivers.setSelectedIndex(0);
		drivers.ensureIndexIsVisible(0);
	}


	/**
	 * Set the car list to select a particular carid if its in the list.
	 * @param carid  the id of the car to select
	 */
	public void focusOnCar(int carid)
	{
		ListModel lm = cars.getModel();
		for (int ii = 0; ii < lm.getSize(); ii++)
		{
			Car c = (Car)lm.getElementAt(ii);
			if (c.getId() == carid)
			{
				cars.setSelectedIndex(ii);
				cars.ensureIndexIsVisible(ii);
				break;
			}
		}
	}


	/**
	 * Reload the carlist based on the selected driver, and optionally select one.
	 * @param select
	 */
	public void reloadCars(Car select)
	{
		log.fine("Reload cars ("+select+")");
		Driver d = (Driver)drivers.getSelectedValue();

		if (d == null) // nothing to do
			return;

		List<Car> driverCars = Database.d.getCarsForDriver(d.getId());
		for (Iterator<Car> citer = driverCars.iterator(); citer.hasNext(); )
		{
			Car c = citer.next();
			c.isRegistered = Database.d.isRegistered(c);
			c.isInRunOrder = Database.d.isInOrder(c.getId());
		}

		cars.setListData(new Vector<Car>(driverCars));
		if (select != null)
			focusOnCar(select.getId());
		else
			cars.setSelectedIndex(0);
	}


	/**
	 * Process events from the various buttons
	 *
	 * @param e 
	 */
	@Override
	public void actionPerformed(ActionEvent e)
	{
		String cmd = e.getActionCommand();

		if (cmd.equals("New Driver"))
		{
			DriverDialog dd = new DriverDialog(new Driver(firstSearch.getText(), lastSearch.getText()));
			dd.doDialog("New Driver", new DialogFinisher<Driver>() {
				@Override
				public void dialogFinished(Driver d) {
					if (d == null) return;
					try {
						Database.d.newDriver(d);
						focusOnDriver(d.getFirstName(), d.getLastName());
					} catch (IOException ioe) {
						log.log(Level.SEVERE, "Failed to create driver: " + ioe, ioe);
					}
				}
			});
		}

		else if (cmd.equals("Edit Driver"))
		{
			DriverDialog dd = new DriverDialog(selectedDriver);
			dd.doDialog("Edit Driver", new DialogFinisher<Driver>() {
				@Override
				public void dialogFinished(Driver d) {
					if (d == null) return;
					try {
						Database.d.updateDriver(d);
						driverInfo.setText(driverDisplay(d));
					} catch (IOException ioe) {
						log.log(Level.SEVERE, "Failed to update driver: " + ioe, ioe);
					}
				}
			});
		}

		else if (cmd.equals("New Car") || cmd.equals("New From"))
		{
			final CarDialog cd;
			if (cmd.equals("New From"))
				cd = new CarDialog(selectedCar, Database.d.getClassData(), carAddOption);
			else
				cd = new CarDialog(null, Database.d.getClassData(), carAddOption);

			cd.doDialog("New Car", new DialogFinisher<Car>() {
				@Override
				public void dialogFinished(Car c) {
					if (c == null)
						return;
					try
					{
						if (selectedDriver != null)
						{
							c.setDriverId(selectedDriver.getId());
							Database.d.newCar(c);
							reloadCars(c);
							if (cd.getAddToRunOrder())
								Messenger.sendEvent(MT.CAR_ADD, c.getId());
						}
					}
					catch (IOException ioe)
					{
						log.log(Level.SEVERE, "Failed to create a car: " + ioe, ioe);
					}
				}
			});
		}

		else if (cmd.equals("Clear"))
		{
			firstSearch.setText("");
			lastSearch.setText("");
			firstSearch.requestFocus();
		}

		else
		{
			log.info("Unknown command in DriverEntry: " + cmd);
		}
	}


	/**
	 * One of the list value selections has changed.
	 * This can be either a user selection or the list model was updated
	 */
	@Override
	public void valueChanged(ListSelectionEvent e) 
	{
		if (e.getValueIsAdjusting() == false)
		{
			Object source = e.getSource();
			if (source == drivers)
			{
				Object o = drivers.getSelectedValue();
				if (o instanceof Driver)
				{
					selectedDriver = (Driver)o;
					driverInfo.setText(driverDisplay(selectedDriver));
					reloadCars(null);
				}
				else
				{
					selectedDriver = null;
					driverInfo.setText("\n\n\n\n");
					cars.setListData(new Vector());
					cars.clearSelection();
				}
			}

			else if (source == cars)
			{
				Object o = cars.getSelectedValue();
				if (o instanceof Car)
				{
					selectedCar = (Car)o;
					carInfo.setText(carDisplay(selectedCar));
				}
				else
				{
					selectedCar = null;
					carInfo.setText("\n");
				}
			}
		}
	}


	public String driverDisplay(Driver d)
	{
		StringBuffer ret = new StringBuffer();
		ret.append(d.getFullName() + "\n");
		ret.append(d.getAddress() + "\n");
		ret.append(d.getCity() + ", " + d.getState() + " " + d.getZip() + "\n");
		ret.append(d.getEmail() + "\n");
		ret.append(d.getHomePhone());
		return ret.toString();
	}


	public String carDisplay(Car c)
	{
		StringBuffer ret = new StringBuffer();
		ret.append(c.getClassCode());
		if (!c.getIndexCode().equals(""))
			ret.append(" (" + c.getIndexCode() + ")");
		ret.append(" - #" + c.getNumber() + "\n");
		ret.append(c.getYear() + " " + c.getMake() + " " + c.getModel() + " " + c.getColor());
		return ret.toString();
	}


	@Override
	public void focusGained(FocusEvent e)
	{
		JTextField tf = (JTextField)e.getComponent();
		tf.selectAll();
	}

	@Override
	public void focusLost(FocusEvent e)
	{
		JTextField tf = (JTextField)e.getComponent();
		tf.select(0,0);
	}
	
	class SearchDrivers extends SearchTrigger
	{
		@Override
		public void search(String txt)
		{
			String first = null, last = null;
			if (lastSearch.getDocument().getLength() > 0) 
				last = lastSearch.getText();
			if (firstSearch.getDocument().getLength() > 0)
				first = firstSearch.getText();
			drivers.setListData(new Vector<Driver>(Database.d.getDriversLike(first, last)));
			drivers.setSelectedIndex(0);
		}
	}
}
