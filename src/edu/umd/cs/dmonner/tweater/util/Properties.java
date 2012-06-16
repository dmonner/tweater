package edu.umd.cs.dmonner.tweater.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An extension of Java's default <code>Properties</code> class with extra convenience methods for
 * returning values as types other than <code>String</code> as well as requiring that values be
 * present.
 * 
 * @author dmonner
 */
public class Properties extends java.util.Properties
{
	private static final long serialVersionUID = 1L;

	/**
	 * Holds the name of the most recent property file loaded
	 */
	private String propfile = "<defaults>";
	/**
	 * Holds properties known to have boolean values
	 */
	private final Map<String, Boolean> boolprops;
	/**
	 * Holds properties known to have integer values
	 */
	private final Map<String, Integer> intprops;
	/**
	 * Holds properties known to have floating-point values
	 */
	private final Map<String, Float> floatprops;
	/**
	 * Holds optional comments about each property
	 */
	private final Map<String, String> comments;
	/**
	 * Order the keys were first inserted; governs how they are output to a file
	 */
	private final List<String> order;

	/**
	 * Create a new, empty property set
	 */
	public Properties()
	{
		super();
		boolprops = new HashMap<String, Boolean>();
		intprops = new HashMap<String, Integer>();
		floatprops = new HashMap<String, Float>();
		comments = new HashMap<String, String>();
		order = new ArrayList<String>();
	}

	/**
	 * Finds all key/value pairs where the values are floats or integers, converts those values to
	 * their numeric types, and adds them to the appropriate type-specific internal collections.
	 */
	public void compile()
	{
		// for each property
		for(final String key : this.stringPropertyNames())
		{
			final String value = this.getProperty(key);

			// put it into intprops map if it parses as an int
			try
			{
				intprops.put(key, Integer.parseInt(value));
			}
			catch(final NumberFormatException ex)
			{
			}

			// put it into floatprops map if it parses as a float
			try
			{
				floatprops.put(key, Float.parseFloat(value));
			}
			catch(final NumberFormatException ex)
			{
			}
		}
	}

	/**
	 * Gets an existing property as a <code>boolean</code>.
	 * 
	 * @param name
	 *          Name of the property to get
	 * @return Value of the property as a <code>boolean</code>
	 * @throws NullPointerException
	 *           If the property does not exist
	 * @throws NumberFormatException
	 *           If the property exists but cannot be converted to a <code>boolean</code>
	 */
	public boolean getBooleanProperty(final String name)
	{
		if(!boolprops.containsKey(name))
		{
			final String s = getProperty(name);
			final boolean value = s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t")
					|| s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("y") || s.equals("1");
			boolprops.put(name, value);
		}

		return boolprops.get(name);
	}

	/**
	 * Gets an existing property as a <code>float</code>.
	 * 
	 * @param name
	 *          Name of the property to get
	 * @return Value of the property as a <code>float</code>
	 * @throws NullPointerException
	 *           If the property does not exist
	 * @throws NumberFormatException
	 *           If the property exists but cannot be converted to a <code>float</code>
	 */
	public float getFloatProperty(final String name)
	{
		if(!floatprops.containsKey(name))
			floatprops.put(name, Float.parseFloat(getProperty(name)));

		return floatprops.get(name);
	}

	/**
	 * Gets an existing property as a <code>int</code>.
	 * 
	 * @param name
	 *          Name of the property to get
	 * @return Value of the property as a <code>int</code>
	 * @throws NullPointerException
	 *           If the property does not exist
	 * @throws NumberFormatException
	 *           If the property exists but cannot be converted to a <code>int</code>
	 */
	public int getIntegerProperty(final String name)
	{
		if(!intprops.containsKey(name))
			intprops.put(name, Integer.parseInt(getProperty(name)));

		return intprops.get(name);
	}

	/**
	 * Reads the file whose name is given as an argument and adds the key-value pairs in that file to
	 * those in this <code>Properties</code> object. Existing properties are retained, except for
	 * properties with the same names as those in the input file, which are overwritten.
	 * 
	 * @param propfile
	 *          The properties file from which to read keys and values
	 * @throws FileNotFoundException
	 *           If the properties file argument cannot be found
	 * @throws IOException
	 *           If any other I/O error occurs
	 */
	public void load(final String propfile) throws FileNotFoundException, IOException
	{
		this.propfile = propfile;
		FileInputStream is = null;

		try
		{
			// open the file
			is = new FileInputStream(propfile);

			// load it with the superclass method
			super.load(is);

			// make sure all properties are converted to the appropriate types
			compile();
		}
		finally
		{
			// make sure the input stream is closed, even in case of error
			if(is != null)
			{
				is.close();
			}
		}
	}

	/**
	 * Checks that a required float property exists in this <code>Properties</code> object.
	 * 
	 * @param name
	 *          The property name to check
	 * @throws IllegalArgumentException
	 *           If the property does not exist or does not parse as a float
	 */
	public void requireFloatProperty(final String name)
	{
		requireFloatProperty(name, Float.MIN_VALUE, Float.MAX_VALUE);
	}

	/**
	 * Checks that a required float property exists in this <code>Properties</code> object and is
	 * within the required bounds <code>[min, max]</code>.
	 * 
	 * @param name
	 *          The property name to check
	 * @param min
	 *          The minimum value allowable
	 * @param max
	 *          The maximum value allowable
	 * @throws IllegalArgumentException
	 *           If the property does not exist, does not parse as a float, or falls outside the
	 *           required bounds
	 */
	public void requireFloatProperty(final String name, final float min, final float max)
	{
		try
		{
			final float value = Float.parseFloat(getProperty(name));
			floatprops.put(name, value);

			if(value < min || value > max)
				throw new IllegalArgumentException("Value for property " + name
						+ " must be a float in the range [" + min + ", " + max + "] in " + propfile);
		}
		catch(final NullPointerException ex)
		{
			throw new IllegalArgumentException("Value for property " + name
					+ " must exist and be a float in " + propfile);
		}
		catch(final NumberFormatException ex)
		{
			throw new IllegalArgumentException("Value for property " + name + " must be a float in "
					+ propfile);
		}
	}

	/**
	 * Checks that a required integer property exists in this <code>Properties</code> object.
	 * 
	 * @param name
	 *          The property name to check
	 * @throws IllegalArgumentException
	 *           If the property does not exist or does not parse as an integer
	 */
	public void requireIntegerProperty(final String name)
	{
		requireIntegerProperty(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * Checks that a required integer property exists in this <code>Properties</code> object and is
	 * greater than or equal to <code>min</code>.
	 * 
	 * @param name
	 *          The property name to check
	 * @param min
	 *          The minimum value allowable
	 * @throws IllegalArgumentException
	 *           If the property does not exist, does not parse as an integer, or falls outside the
	 *           required bounds
	 */
	public void requireIntegerProperty(final String name, final int min)
	{
		requireIntegerProperty(name, min, Integer.MAX_VALUE);
	}

	/**
	 * Checks that a required integer property exists in this <code>Properties</code> object and is
	 * within the required bounds <code>[min, max]</code>.
	 * 
	 * @param name
	 *          The property name to check
	 * @param min
	 *          The minimum value allowable
	 * @param max
	 *          The maximum value allowable
	 * @throws IllegalArgumentException
	 *           If the property does not exist, does not parse as an integer, or falls outside the
	 *           required bounds
	 */
	public void requireIntegerProperty(final String name, final int min, final int max)
	{
		try
		{
			final int value = Integer.parseInt(getProperty(name));
			intprops.put(name, value);

			if(value < min || value > max)
				throw new IllegalArgumentException("Value for property " + name
						+ " must be an integer in the range [" + min + ", " + max + "] in " + propfile);
		}
		catch(final NullPointerException ex)
		{
			throw new IllegalArgumentException("Value for property " + name
					+ " must exist and be an integer in " + propfile);
		}
		catch(final NumberFormatException ex)
		{
			throw new IllegalArgumentException("Value for property " + name + " must be an integer in "
					+ propfile);
		}
	}

	/**
	 * Checks that a required property exists in this <code>Properties</code> object.
	 * 
	 * @param name
	 *          The property name to check
	 * @throws IllegalArgumentException
	 *           If the property does not exist
	 */
	public void requireProperty(final String name)
	{
		if(!containsKey(name) || getProperty(name).trim().isEmpty())
			throw new IllegalArgumentException("Missing value for required property " + name + " in "
					+ propfile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Properties#save(java.io.OutputStream, java.lang.String)
	 */
	@Override
	public void save(final OutputStream os, final String header)
	{
		// Print the header
		final PrintWriter out = new PrintWriter(os);
		out.println("# " + header);
		out.println();

		// Print all the properties with comments, in the order they were added
		for(final String name : order)
		{
			final String value = getProperty(name);
			out.println("# " + comments.get(name));
			out.println((value.isEmpty() ? "#" : "") + name + " = " + value);
			out.println();
		}

		// Print any other properties in random, hashtable order
		for(final String name : stringPropertyNames())
		{
			if(!order.contains(name))
			{
				final String value = getProperty(name);
				out.println((value.isEmpty() ? "#" : "") + name + " = " + value);
			}
		}

		out.flush();
	}

	/**
	 * Sets the given property name to the given value and also adds a comment string that will
	 * accompany the value in the properties file if written to disk.
	 * 
	 * @param name
	 * @param value
	 * @param comment
	 */
	public void setProperty(final String name, final String value, final String comment)
	{
		setProperty(name, value);
		comments.put(name, comment);
		if(!order.contains(name))
			order.add(name);
	}
}
