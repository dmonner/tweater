package edu.umd.cs.dmonner.tweater;

/**
 * A simple container that pairs a <code>QueryItem</code> with a start and end time, as is necessary
 * internally to a <code>QueryBuilder</code>.
 * 
 * @author dmonner
 */
public class QueryItemTime
{
	/**
	 * A <code>QueryItem</code> that is given time bounds.
	 */
	public final QueryItem item;
	/**
	 * The time (in ms since the epoch) that the associated <code>QueryItem</code> should start
	 * matching.
	 */
	public final long startTime;
	/**
	 * The time (in ms since the epoch) that the associated <code>QueryItem</code> should stop
	 * matching.
	 */
	public final long endTime;

	/**
	 * @param item
	 * @param startTime
	 * @param endTime
	 */
	public QueryItemTime(final QueryItem item, final long startTime, final long endTime)
	{
		this.item = item;
		this.startTime = startTime;
		this.endTime = endTime;
	}
}
