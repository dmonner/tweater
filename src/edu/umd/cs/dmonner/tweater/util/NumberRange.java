package edu.umd.cs.dmonner.tweater.util;

/**
 * Represents a range of integers, inclusive on either end. Allows this range to be converted to
 * text and SQL. Capable of extracting ranges from strings such as <code>13-21</code> or
 * <code>[8-44]</code>.
 * 
 * @author dmonner
 */
public class NumberRange
{
	/**
	 * The smallest number included in the range
	 */
	public final int min;
	/**
	 * The largest number included in the range
	 */
	public final int max;
	/**
	 * The original string source of the range, if any
	 */
	public final String from;

	/**
	 * Creates a new number range based on two integer end points.
	 * 
	 * @param min
	 * @param max
	 * @throws IllegalArgumentException
	 *           If min > max
	 */
	public NumberRange(final int min, final int max)
	{
		if(min > max)
			throw new IllegalArgumentException("Min cannot be greater than max.");

		this.min = min;
		this.max = max;
		this.from = null;
	}

	/**
	 * Creates a new number range based on a string such as <code>13-21</code> or <code>[8-44]</code>.
	 * 
	 * @param from
	 * @throws IllegalArgumentException
	 *           If the string has mismatched brackets
	 * @throws NumberFormatException
	 *           If any number in the string fails to parse
	 */
	public NumberRange(final String from)
	{
		String str = from.trim();
		final boolean sw = str.startsWith("[");
		final boolean ew = str.endsWith("]");

		if(sw != ew)
			throw new IllegalArgumentException("Mismatched []s: " + from);

		// trim the string by removing brackets if they exist
		if(sw && ew)
			str = str.substring(1, str.length() - 1).trim();

		// find the first hyphen
		final int hyphen = str.indexOf('-');

		if(hyphen >= 0)
		{
			// split the string on the hyphen
			this.min = Integer.parseInt(str.substring(0, hyphen).trim());
			this.max = Integer.parseInt(str.substring(hyphen + 1).trim());
		}
		else
		{
			// if no hyphen exists, this is a singular set
			this.min = this.max = Integer.parseInt(str);
		}

		this.from = from;
	}

	/**
	 * Determines whether an arbitrary number falls inside the range defined by this object.
	 * 
	 * @param num
	 * @return <code>true</code> iff num falls inside the defined range
	 */
	public boolean contains(final int num)
	{
		return min <= num && num <= max;
	}

	/**
	 * Determines whether the given range has a non-null intersection with this range.
	 * 
	 * @param that
	 * @return <code>true</code> iff the two ranges have numbers in common
	 */
	public boolean overlaps(final NumberRange that)
	{
		return !(this.max < that.min || that.max < this.min);
	}

	/**
	 * Builds a string representing the range as a SQL <code>WHERE</code> condition on the given
	 * variable name.
	 * 
	 * @param varname
	 *          The variable name whose range is defined by this object
	 * @return SQL code to put in a <code>WHERE</code> condition that restricts the domain of
	 *         <code>varname</code> to the values represented by this object
	 */
	public String toSQL(final String varname)
	{
		final StringBuilder sb = new StringBuilder();

		if(min < max)
		{
			sb.append(min);
			sb.append(" <= ");
			sb.append(varname);
			sb.append(" AND ");
			sb.append(varname);
			sb.append(" <= ");
			sb.append(max);
		}
		else
		{
			sb.append(varname);
			sb.append(" = ");
			sb.append(min);
		}

		return sb.toString();
	}

	/**
	 * @return This range represented as an unbracketed string
	 */
	@Override
	public String toString()
	{
		return toString(false);
	}

	/**
	 * @param bracketed
	 *          Whether or not to add enclosing square brackets around the result string.
	 * @return This range represented as a string
	 */
	public String toString(final boolean bracketed)
	{
		final StringBuilder sb = new StringBuilder();

		if(bracketed)
			sb.append("[");
		sb.append(min);
		if(min < max)
		{
			sb.append(", ");
			sb.append(max);
		}
		if(bracketed)
			sb.append("]");

		return sb.toString();
	}
}
