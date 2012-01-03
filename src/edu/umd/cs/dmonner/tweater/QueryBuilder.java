package edu.umd.cs.dmonner.tweater;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * This class builds a set of query phrases that are currently active based on an external data
 * source. The data source should specify what all query phrases to be collected, as well as their
 * start and end times. This class runs the <code>update</code> method once per hour in order to
 * re-sync with the data source, as that is where query changes should be made.
 * 
 * @author dmonner
 */
public abstract class QueryBuilder extends Thread
{
	/**
	 * The identifier of the log file
	 */
	protected final String id;
	/**
	 * The interval (ms) between updates from the data source
	 */
	protected final long buildInterval;
	/**
	 * The time (in ms since the epoch) at which we should next update the query from the data source
	 */
	protected long nextUpdate;
	/**
	 * If <code>true</code>, we should stop updating
	 */
	protected boolean shutdown;
	/**
	 * Parallel-indexed with <code>times</code>. The idea is that the <code>times</code> list contains
	 * all the times at which the query changes. The first index is when the first query begins, and
	 * the last index is when the last query ends. If the current time is between
	 * <code>times[i]</code> and <code>times[i+1]</code>, the current set of query phrases will be in
	 * <code>queries[i+1]</code>. <code>queries[0]</code> should always contain an empty set of
	 * queries, to be returned before/after the time bounds.
	 * 
	 * Both of these data structures should be built from the data source, from scratch, every time
	 * the <code>update</code> method is called.
	 */
	protected final ArrayList<TreeSet<QueryItem>> queries;
	/**
	 * Parallel-indexed with <code>queries</code>; see that variable's description.
	 */
	protected final ArrayList<Long> times;
	/**
	 * The log file
	 */
	protected final Logger log;

	public QueryBuilder(final String id, final Properties props)
	{
		this.id = id;
		this.log = Logger.getLogger(id);
		this.buildInterval = props.getIntegerProperty("tweater.builder.interval") * 1000;
		this.times = new ArrayList<Long>();
		this.queries = new ArrayList<TreeSet<QueryItem>>();
		this.nextUpdate = 0; // always update right away
		this.shutdown = false;
	}

	/**
	 * Based on the information from the data source, constructs the query that would be active at the
	 * specified time.
	 * 
	 * @param time
	 * @return The query active at the given time
	 */
	public TreeSet<QueryItem> at(final long time)
	{
		synchronized(queries)
		{
			if(times.isEmpty())
				return new TreeSet<QueryItem>();

			// if the time is less than the first entry in the array, query is empty (queries[0])
			if(time < times.get(0))
				return queries.get(0);

			// otherwise, search array for the appropriate slot by finding first time greater
			for(int i = 1; i < times.size(); i++)
				if(time < times.get(i))
					return queries.get(i);

			// otherwise, time > biggest time, so query is empty (queries[0])
			return queries.get(0);
		}
	}

	/**
	 * @return The next time (ms since the epoch) that we need to update the query
	 */
	protected long calculateNextUpdate()
	{
		return ((new Date().getTime() / buildInterval) + 1) * buildInterval;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run()
	{
		// Main loop periodically updates the query from the data source
		while(!shutdown)
		{
			if(new Date().getTime() > nextUpdate)
			{
				final List<QueryItemTime> all = update();
				if(all != null)
					set(all);
				nextUpdate = calculateNextUpdate();
			}

			// Wait a while before starting the loop again
			try
			{
				Thread.sleep((int)(5000.0 + Math.random() * 1000.0));
			}
			catch(final InterruptedException ex)
			{
				Logger.getLogger(id).severe(Util.traceMessage(ex));
			}
		}

		Logger.getLogger(id).info("QueryBuilder shut down.");
	}

	/**
	 * Uses the most recent query information from the data source to intelligently update the query
	 * builder's timeline.
	 * 
	 * @param all
	 *          The most recent query information from the data source
	 */
	public void set(final List<QueryItemTime> all)
	{
		synchronized(queries)
		{
			final TreeSet<Long> alltimes = new TreeSet<Long>();
			for(final QueryItemTime qpt : all)
			{
				alltimes.add(qpt.startTime);
				alltimes.add(qpt.endTime);
			}
			times.clear();
			times.addAll(alltimes);

			queries.clear();
			for(int i = 0; i < times.size(); i++)
				queries.add(new TreeSet<QueryItem>());

			for(final QueryItemTime qpt : all)
				for(int i = 0; i < times.size(); i++)
					if(qpt.startTime <= times.get(i) && times.get(i) < qpt.endTime)
						queries.get(i + 1).add(qpt.item);
		}
	}

	/**
	 * Instructs the query builder to stop checking the data source for query updates
	 */
	public void shutdown()
	{
		shutdown = true;
	}

	/**
	 * Reads all query information from the data source and returns it, without regard to the
	 * information that the query builder currently knows; this will be computed by <code>set</code>
	 * separately.
	 * 
	 * @return A list of query items and associated times, fresh from the data source.
	 */
	public abstract List<QueryItemTime> update();
}
