package edu.umd.cs.dmonner.tweater.util;

/**
 * Represents a possibly discontinuous set of integers as a collection of <code>NumberRange</code>
 * objects. Allows this set to be converted to text and SQL. Capable of extracting sets from strings
 * such as <code>4,8-10,13-21</code> or <code>53,[8-44],[6]</code>.
 * 
 * @author dmonner
 * 
 */
public class NumberSet
{
	/**
	 * The original string source of the set, if any
	 */
	public final String from;
	/**
	 * The ranges that comprise this set
	 */
	public final NumberRange[] ranges;

	/**
	 * Creates a new set from a string of comma-separated ranges.
	 * 
	 * @param from
	 * @throws IllegalArgumentException
	 *           If any of the ranges fails to parse, or if any of the given ranges overlaps another
	 *           range
	 */
	public NumberSet(final String from)
	{
		this.from = from;

		// split on commas and allow the NumberRange constructor to parse each substring
		final String[] froms = from.split(",");
		this.ranges = new NumberRange[froms.length];
		for(int i = 0; i < froms.length; i++)
			this.ranges[i] = new NumberRange(froms[i]);

		// check the resulting ranges for overlap with each other; there are better ways to do this, but
		// efficiency is not a major concern here.
		for(int i = 0; i < this.ranges.length; i++)
			for(int j = i + 1; j < this.ranges.length; j++)
				if(this.ranges[i].overlaps(this.ranges[j]))
					throw new IllegalArgumentException("Overlapping ranges: " + this.ranges[i] + " and "
						+ this.ranges[j]);
	}

	/**
	 * Builds a string representing the set as a SQL <code>WHERE</code> condition on the given
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

		sb.append("(");
		sb.append(ranges[0].toSQL(varname));
		sb.append(")");

		for(int i = 1; i < ranges.length; i++)
		{
			sb.append(" OR ");
			sb.append("(");
			sb.append(ranges[i].toSQL(varname));
			sb.append(")");
		}

		return sb.toString();
	}

	/**
	 * @return This set represented as an unbracketed string
	 */
	@Override
	public String toString()
	{
		return toString(false);
	}

	/**
	 * @param bracketed
	 *          Whether or not to add enclosing square brackets around each range
	 * @return This set represented as a string
	 */
	public String toString(final boolean bracketed)
	{
		final StringBuilder sb = new StringBuilder();

		sb.append(ranges[0].toString(bracketed));

		for(int i = 1; i < ranges.length; i++)
		{
			sb.append(", ");
			sb.append(ranges[i].toString(bracketed));
		}

		return sb.toString();
	}
}
