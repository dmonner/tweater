package edu.umd.cs.dmonner.tweater;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.mail.MessagingException;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import edu.umd.cs.dmonner.tweater.util.AlertEmailer;
import edu.umd.cs.dmonner.tweater.util.LogDiffKeeper;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * This class is an intermediary between a <code>Querier</code> and a <code>StatusEater</code> that
 * uses a thread pool to allow many statuses to be persisted in parallel. The queue holding statuses
 * to be processed may grow without bound, limited only by available memory allocated to the JVM.
 * The queue can be configured to produce periodic log and email messages about the state of the
 * queue, such as the number of statuses that have been track-limited (due to an overly broad
 * Twitter query), the fraction of available memory in use, and the number of statuses that have
 * been rejected to prevent resource over-utilization.
 * 
 * @author dmonner
 */
public class StatusQueue extends Thread implements StatusListener
{
	/**
	 * The thread pool allowing statuses to be processed in parallel
	 */
	private final ThreadPoolExecutor pool;
	/**
	 * The class that processes and persists statuses
	 */
	private final StatusEater eater;
	/**
	 * The destination for email alert messages
	 */
	private final AlertEmailer alert;
	/**
	 * The log file
	 */
	private final Logger log;
	/**
	 * The log handler that keeps log diffs used in status messages
	 */
	private final LogDiffKeeper logdiff;
	/**
	 * The name of the host machine
	 */
	private final String host = Util.getHost();
	/**
	 * The total number of rejected executions so far
	 */
	private int rejectedExecutions = 0;
	/**
	 * The last time (in ms since the epoch) that a rejected-executions message was logged; used to
	 * prevent log spamming
	 */
	private long rejectionMessageLastUpdate = 0;
	/**
	 * <code>true</code> iff we need to log a rejected-executions message the next time we are able
	 */
	private boolean rejectionMessageNeedsUpdate = false;
	/**
	 * The minimum interval (ms) between logging rejected-executions messages
	 */
	private final int rejectionMessageInterval;
	/**
	 * The last time (in ms since the epoch) that a status message was sent via email
	 */
	private long statusEmailNextUpdate = 0;
	/**
	 * The interval (ms) between emailing status messages
	 */
	private final int statusEmailInterval;
	/**
	 * The last time (in ms since the epoch) that a rejected-executions message was sent via email;
	 * used to prevent email spamming
	 */
	private long rejectionEmailLastUpdate = 0;
	/**
	 * <code>true</code> iff we need to email a rejected-executions message the next time we are able
	 */
	private boolean rejectionEmailNeedsUpdate = false;
	/**
	 * The minimum interval (ms) between emailing rejected-executions messages
	 */
	private final int rejectionEmailInterval;
	/**
	 * The total number of track limitations so far
	 */
	private int trackLimitations = 0;
	/**
	 * The last time (in ms since the epoch) that a track-limitations message was logged; used to
	 * prevent log spamming
	 */
	private long trackLimitMessageLastUpdate = 0;
	/**
	 * <code>true</code> iff we need to log a track-limitations message the next time we are able
	 */
	private boolean trackLimitMessageNeedsUpdate = false;
	/**
	 * The minimum interval (ms) between logging rejected-executions messages
	 */
	private final int trackLimitMessageInterval;
	/**
	 * The threshold, as a fraction of available memory, at and above which we explicitly reject new
	 * additions to the queue so as to prevent <code>OutOfMemoryError</code>s.
	 */
	private final float resourceLimitRejectionThreshold;
	/**
	 * The threshold, as a fraction of available memory, at and above which we periodically log a
	 * resource limitation message. After reaching this threshold, we will also log the first instance
	 * when we fall back below the threshold.
	 */
	private final float resourceLimitMessageThreshold;
	/**
	 * The last time (in ms since the epoch) that a resource-limitations message was logged; used to
	 * prevent log spamming
	 */
	private long resourceLimitMessageLastUpdate = 0;
	/**
	 * <code>true</code> iff we need to email a resource-limitations message the next time we are able
	 */
	private boolean resourceLimitMessageNeedsUpdate = false;
	/**
	 * The minimum interval (ms) between logging resource-limitations messages
	 */
	private final int resourceLimitMessageInterval;
	/**
	 * The threshold, as a fraction of available memory, at and above which we periodically email a
	 * resource limitation message. After reaching this threshold, we will also email at the first
	 * instance when we fall back below the threshold.
	 */
	private final float resourceLimitEmailThreshold;
	/**
	 * The last time (in ms since the epoch) that a resource-limitations message was sent via email;
	 * used to prevent email spamming
	 */
	private long resourceLimitEmailLastUpdate = 0;
	/**
	 * <code>true</code> iff we need to email a resource-limitations message the next time we are able
	 */
	private boolean resourceLimitEmailNeedsUpdate = false;
	/**
	 * The minimum interval (ms) between emailing resource-limitations messages
	 */
	private final int resourceLimitEmailInterval;

	/**
	 * Creates a new status queue using the given properties.
	 * 
	 * @param id
	 *          The ID of the log file to use
	 * @param eater
	 *          The consumer of statuses
	 * @param prop
	 *          The TwEater configuration
	 * @param alert
	 *          The destination for email alerts
	 */
	public StatusQueue(final String id, final StatusEater eater, final Properties prop,
			final AlertEmailer alert)
	{
		this.eater = eater;
		this.alert = alert;
		log = Logger.getLogger(id);
		logdiff = new LogDiffKeeper();
		log.addHandler(logdiff);
		log.info("Initializing StatusQueue.");

		// -- Create an unbounded Executor queue with appropriate parameters

		pool = new ThreadPoolExecutor( //
				prop.getIntegerProperty("tweater.queue.coreThreads"), // in threads
				prop.getIntegerProperty("tweater.queue.maxThreads"), // in threads
				prop.getIntegerProperty("tweater.queue.idleTimeout"), // in s
				TimeUnit.SECONDS, //
				new LinkedBlockingQueue<Runnable>());

		// -- Read in properties about status emails and set up the times

		final int statusEmailHour = prop.getIntegerProperty("tweater.queue.statusEmailHour");
		statusEmailInterval = 1000 * prop.getIntegerProperty("tweater.queue.statusEmailInterval");
		final Calendar first = Calendar.getInstance();
		first.set(Calendar.AM_PM, statusEmailHour < 12 ? Calendar.AM : Calendar.PM);
		first.set(Calendar.HOUR, statusEmailHour % 12);
		first.set(Calendar.MINUTE, 0);
		first.set(Calendar.SECOND, 0);
		first.set(Calendar.MILLISECOND, 0);
		statusEmailNextUpdate = first.getTimeInMillis();
		final long now = new Date().getTime();
		while(statusEmailNextUpdate < now)
		{
			statusEmailNextUpdate += statusEmailInterval;
		}

		// -- Read in the message intervals from the properties file, converting s to ms

		rejectionMessageInterval = 1000 * prop
				.getIntegerProperty("tweater.queue.rejectionMessageInterval");
		rejectionEmailInterval = 1000 * prop.getIntegerProperty("tweater.queue.rejectionEmailInterval");
		trackLimitMessageInterval = 1000 * prop
				.getIntegerProperty("tweater.queue.trackLimitMessageInterval");
		resourceLimitMessageInterval = 1000 * prop
				.getIntegerProperty("tweater.queue.resourceLimitMessageInterval");
		resourceLimitEmailInterval = 1000 * prop
				.getIntegerProperty("tweater.queue.resourceLimitEmailInterval");

		// -- Read in the resource limitation thresholds from the properties file

		resourceLimitMessageThreshold = prop
				.getFloatProperty("tweater.queue.resourceLimitMessageThreshold");
		resourceLimitEmailThreshold = prop
				.getFloatProperty("tweater.queue.resourceLimitEmailThreshold");
		resourceLimitRejectionThreshold = prop
				.getFloatProperty("tweater.queue.resourceLimitRejectionThreshold");
	}

	/**
	 * Adds a <code>QueryItem</code> to the queue's associated <code>StatusEater</code>.
	 * 
	 * @param item
	 */
	public void addItem(final QueryItem item)
	{
		eater.addItem(item);
	}

	/**
	 * Removes a specific <code>QueryItem</code> from the queue's associated <code>StatusEater</code>.
	 * 
	 * @param item
	 */
	public void delItem(final QueryItem item)
	{
		eater.delItem(item);
	}

	/**
	 * @return <code>true</code> iff the queue has been shutdown AND has completed its backlog
	 */
	public boolean finished()
	{
		return pool.isTerminated();
	}

	/**
	 * @return The collection of <code>QueryItem</code>s held by the queue's associated
	 *         <code>StatusEater</code>
	 */
	public Set<QueryItem> getQuery()
	{
		return eater.getQuery();
	}

	/**
	 * @return The size of the queue
	 */
	public int getQueueSize()
	{
		return pool.getQueue().size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see twitter4j.StatusListener#onDeletionNotice(twitter4j.StatusDeletionNotice)
	 */
	@Override
	public void onDeletionNotice(final StatusDeletionNotice statusDeletionNotice)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see twitter4j.StreamListener#onException(java.lang.Exception)
	 */
	@Override
	public void onException(final Exception ex)
	{
		if(!ex.getMessage().contains("Stream closed"))
		{
			log.severe(Util.traceMessage(ex));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see twitter4j.StatusListener#onScrubGeo(int, long)
	 */
	@Override
	public void onScrubGeo(final int userId, final long upToStatusId)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see twitter4j.StatusListener#onStatus(twitter4j.Status)
	 */
	@Override
	public void onStatus(final Status status)
	{
		// If we are below the memory utilization threshold
		if(Util.getMemoryUtilizationPercent() < resourceLimitRejectionThreshold)
		{
			// Create and submit a Runnable that will process the status
			pool.execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						log.finest("Calling StatusEater.process() on status id " + status.getId());
						eater.process(status);
					}
					catch(final Exception ex)
					{
						log.severe("Unhandled error in ThreadPool thread:\n" + Util.traceMessage(ex));
					}
				}
			});
		}
		// Otherwise, reject the status and log that fact
		else
		{
			log.finest("Rejected execution on status id " + status.getId());
			rejectedExecutions++;
			rejectionMessageNeedsUpdate = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see twitter4j.StatusListener#onTrackLimitationNotice(int)
	 */
	@Override
	public void onTrackLimitationNotice(final int numberOfLimitedStatuses)
	{
		log.finest("Track Limitation notice: " + numberOfLimitedStatuses);
		trackLimitations = numberOfLimitedStatuses;
		trackLimitMessageNeedsUpdate = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run()
	{
		final Runtime rt = Runtime.getRuntime();

		// This loop waits until the queue is done processing statuses, periodically logging and
		// emailing warning messages
		while(!finished())
		{
			final long now = new Date().getTime();

			// Email a status message
			if(statusEmailInterval >= 0 && now > statusEmailNextUpdate)
			{
				statusEmailNextUpdate = statusEmailNextUpdate + statusEmailInterval;
				try
				{
					alert.send("TwEater status: " + host, logdiff.get());
				}
				catch(final MessagingException ex)
				{
					log.severe(Util.traceMessage(ex));
				}
				log.fine("Sent status email.");
			}

			// Log a rejected-executions message if necessary
			if(rejectionMessageNeedsUpdate && rejectionMessageInterval >= 0
					&& now > rejectionMessageLastUpdate + rejectionMessageInterval)
			{
				rejectionMessageLastUpdate = now;
				rejectionMessageNeedsUpdate = false;
				log.severe("Rejected Executions to date: " + rejectedExecutions);
			}

			// Email a rejected-executions message if necessary
			if(rejectionEmailNeedsUpdate && rejectionEmailInterval >= 0
					&& now > rejectionEmailLastUpdate + rejectionEmailInterval)
			{
				rejectionEmailLastUpdate = now;
				rejectionEmailNeedsUpdate = false;
				try
				{
					alert.send("Rejected Exs at " + rejectedExecutions + " on " + host,
							"Rejected Executions to date: " + rejectedExecutions + " on host " + host);
				}
				catch(final MessagingException ex)
				{
					log.severe(Util.traceMessage(ex));
				}
				log.fine("Sent Rejected Execution email.");
			}

			// Log a track-limitations message if necessary
			if(trackLimitMessageNeedsUpdate && trackLimitMessageInterval >= 0
					&& now > trackLimitMessageLastUpdate + trackLimitMessageInterval)
			{
				trackLimitMessageLastUpdate = now;
				trackLimitMessageNeedsUpdate = false;
				log.warning("Track Limitations to date: " + trackLimitations);
			}

			// Compute the amount of memory used and available
			final long used = rt.totalMemory() - rt.freeMemory();
			final long avail = rt.maxMemory();
			final float pct = (float) used / avail;

			// Log a resource-limitations message if necessary
			if((resourceLimitMessageNeedsUpdate || pct > resourceLimitMessageThreshold)
					&& resourceLimitMessageInterval >= 0
					&& now > resourceLimitMessageLastUpdate + resourceLimitMessageInterval)
			{
				resourceLimitMessageLastUpdate = now;
				resourceLimitMessageNeedsUpdate = pct > resourceLimitMessageThreshold;
				log.warning("Resource utilization at " + used / Util.MB + "MB/" + avail / Util.MB + "MB ("
						+ (int) (pct * 100) + "%)");
			}

			// Email a resource-limitations message if necessary
			if((resourceLimitEmailNeedsUpdate || pct > resourceLimitEmailThreshold)
					&& resourceLimitEmailInterval >= 0
					&& now > resourceLimitEmailLastUpdate + resourceLimitEmailInterval)
			{
				resourceLimitEmailLastUpdate = now;
				resourceLimitEmailNeedsUpdate = pct > resourceLimitEmailThreshold;
				try
				{
					alert.send("Resources at " + pct + "% on " + host, "StatusQueue resource utilization at "
							+ used / Util.MB + "MB/" + avail / Util.MB + "MB (" + (int) (pct * 100)
							+ "%) on host " + host);
				}
				catch(final MessagingException ex)
				{
					log.severe(Util.traceMessage(ex));
				}
				log.fine("Sent Resource Limit email.");
			}

			// Wait a little while to start the loop again
			try
			{
				Thread.sleep((int) (5000.0 + Math.random() * 1000.0));
			}
			catch(final InterruptedException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}

		log.info("StatusServer shut down.");
	}

	/**
	 * Prevents the queue from accepting new jobs (but allows it to continue working on jobs already
	 * in the queue).
	 */
	public void shutdown()
	{
		pool.shutdown();
	}
}
