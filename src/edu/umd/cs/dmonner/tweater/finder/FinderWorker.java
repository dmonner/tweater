package edu.umd.cs.dmonner.tweater.finder;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import edu.umd.cs.dmonner.tweater.TwEater;
import edu.umd.cs.dmonner.tweater.util.OneLineFormatter;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * Receives status IDs to look up from a <code>Finder</code> instance and uses the Twitter REST API
 * to find complete information about these statuses. Reports this information back to the calling
 * <code>Finder</code> instance.
 * 
 * @author dmonner
 */
public class FinderWorker extends Thread implements FinderWorkerControl
{
	/**
	 * The log identifier; static because there should be no more than one worker per machine.
	 */
	public static final String id = "finderworker";

	/**
	 * Starts a FinderWorker from the command line
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception
	{
		if(args.length < 1)
		{
			System.out.println("USAGE: worker <server_host> [<port>]");
		}
		else
		{
			final String propfile = args[0];
			final String server = args[1];

			int port = 23890;
			if(args.length > 1)
				port = Integer.parseInt(args[2]);

			new FinderWorker(propfile, server, port).start();
		}
	}

	/**
	 * The hostname of the server where the Finder is running
	 */
	private final String server;
	/**
	 * The configuration properties
	 */
	private final Properties prop;
	/**
	 * The queue of status IDs to look up
	 */
	private final List<Long> queue = new LinkedList<Long>();
	/**
	 * The queue of statuses to send back to the Finder
	 */
	private final List<Status> squeue = new LinkedList<Status>();
	/**
	 * The next time (in ms since the epoch) when this worker will be ready to look up statuses.
	 */
	private long readyAt = 0L;
	/**
	 * The number of Twitter REST API hits remaining
	 */
	private int remaining = 150;
	/**
	 * Twitter REST API interface
	 */
	private final Twitter api = new TwitterFactory().getInstance();
	/**
	 * The log file
	 */
	private final Logger log;
	/**
	 * Whether or not we should be looking up statuses
	 */
	private boolean running = true;

	/**
	 * @param propfile
	 *          The properties file specifiying the TwEater configuration this worker uses
	 * @param server
	 *          The hostname where the Finder process resides
	 * @param port
	 *          The port on which to listen for remote commands
	 * @throws Exception
	 */
	public FinderWorker(final String propfile, final String server, final int port) throws Exception
	{
		// Read the configuration properties
		this.server = server;
		this.prop = TwEater.loadPropertiesFile(propfile);

		// Initialize logging
		final String host = Util.getHost();
		this.log = Logger.getLogger(id);
		final Handler filehandler = new FileHandler(id + "_" + host + ".log", 100 * Util.MB, 1);
		filehandler.setFormatter(new OneLineFormatter());
		this.log.addHandler(filehandler);
		this.log.setLevel(Level.parse(prop.getProperty("tweater.logging.level")));
		this.log.info("Initializing " + id);

		// Register for remote commands
		final FinderWorkerControl stub = (FinderWorkerControl) UnicastRemoteObject.exportObject(this,
				port);
		LocateRegistry.getRegistry().rebind(id, stub);
		log.info("Registered " + id + " successfully on " + host + ", port " + port);
	}

	/**
	 * Resets the number of remanining hits after the API refresh time has been reached.
	 */
	private synchronized void checkReady()
	{
		if(remaining == 0 && new Date().getTime() > readyAt)
		{
			log.info("No longer rate limited: " + new Date().getTime() + " > " + readyAt);
			remaining = 150;
		}
	}

	/**
	 * Returns all unprocessed status IDs in the pipeline to the Finder process for reassignment.
	 * Called when this worker runs out of API requests but still has a pending queue of status IDs.
	 */
	private synchronized void dequeue()
	{
		if(!queue.isEmpty())
		{
			try
			{
				log.finer("Returning " + queue.size() + " status ids to the server.");
				final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(server).lookup(
						Finder.id);
				remote.add(queue);
				queue.clear();
				log.finer("Returned status id list successfully.");
			}
			catch(final NotBoundException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
			catch(final RemoteException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderWorkerControl#enqueue(java.util.List)
	 */
	@Override
	public synchronized void enqueue(final List<Long> status_ids)
	{
		log.info("Received " + status_ids.size() + " status ids from the server.");
		queue.addAll(status_ids);
	}

	/**
	 * Calls <code>ignore</code> on the Finder process for the given status; called when we receive a
	 * null result from Twitter for any reason.
	 * 
	 * @param status_id
	 */
	private synchronized void ignore(final long status_id)
	{
		try
		{
			log.finer("Sending server ignore signal for " + status_id + ".");
			final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(server).lookup(
					Finder.id);
			remote.ignore(status_id);
			log.finer("Sent ignore signal for " + status_id + " successfully.");
		}
		catch(final NotBoundException ex)
		{
			log.severe(Util.traceMessage(ex));
		}
		catch(final RemoteException ex)
		{
			log.severe(Util.traceMessage(ex));
		}
	}

	/**
	 * If there are statuses to be processed and we have API hits remaining, looks up the next status
	 * via the Twitter API. If successful, adds the new status information to the <code>squeue</code>
	 * so it can be sent back to the <code>Finder</code>. Resets the <code>readyAt</code> time and the
	 * <code>remaining</code> hits afterward.
	 */
	private synchronized void lookupNext()
	{
		if(!queue.isEmpty() && remaining > 0)
		{
			final long status_id = queue.remove(0);

			log.finer("Looking up " + status_id);
			Status status = null;
			remaining = -1;
			try
			{
				// Call the showStatus API method to get the status details
				status = api.showStatus(status_id);

				// If successful, use the result to update the ready time and remaining API hits
				final RateLimitStatus rls = status.getRateLimitStatus();
				remaining = rls.getRemaining();
				readyAt = rls.getResetTimeInSeconds() * 1000;
			}
			catch(final TwitterException ex)
			{
				// If there was a problem with the API call, we still need to update the ready time & hits
				final RateLimitStatus rls = ex.getRateLimitStatus();
				remaining = -1;

				if(rls != null)
					remaining = rls.getRemaining();

				// If we are rate limited, note the time and log the event
				if(remaining == 0)
				{
					readyAt = rls.getResetTimeInSeconds() * 1000;
					log.info("Rate Limited until " + readyAt);
				}
				// Log a message if the status is from a suspended account and thus inaccessible
				else if(ex.getMessage().contains("This account is currently suspended"))
				{
					log.info("Twitter says: Status was from suspended account.");
				}
				// Log a message if the user or status have been deleted
				else if(ex.getMessage().contains("resource requested, such as a user, does not exist"))
				{
					log.info("Twitter says: Status (or user) has been deleted.");
				}
				// Log a message if the status is private
				else if(ex.getMessage().contains("Sorry, you are not authorized to see this status"))
				{
					log.info("Twitter says: Status is protected.");
				}
				// Otherwise log the unknown error message
				else
				{
					log.severe(Util.traceMessage(ex));
				}
			}

			// If we got a good result, get ready to send it back to the Finder
			if(status != null)
			{
				squeue.add(status);
				log.finer("Received a valid status.");
			}
			// Otherwise tell the Finder not to expect a result from us
			else
			{
				ignore(status_id);
				log.finer("Received null.");
			}

			log.fine("Hits Remaining: " + remaining);
		}
	}

	/**
	 * Calls <code>process</code> on the Finder for the next status waiting to be sent over.
	 */
	private synchronized void processNext()
	{
		if(!squeue.isEmpty())
		{
			final Status status = squeue.get(0);
			try
			{
				log.finer("Returning status " + status.getId() + " for processing on server.");
				final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(server).lookup(
						Finder.id);
				remote.process(status);
				squeue.remove(0);
				log.finer("Returned status " + status.getId() + " successfully.");
			}
			catch(final NotBoundException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
			catch(final RemoteException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run()
	{
		// Loop until we're told to stop running, looking up statuses each time through.
		while(running)
		{
			// Unblock us if we're rate limited and the time has elapsed
			checkReady();

			// Use the Twitter REST API to look up the next status
			lookupNext();

			// Send the next received status back to the Finder for processing
			processNext();

			// If we're rate limited, send the queue back to the Finder
			if(remaining == 0)
				dequeue();

			// Wait a little while between iterations
			try
			{
				Thread.sleep((int) (1000.0 + Math.random() * 1000.0));
			}
			catch(final InterruptedException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}

		// After shutdown, de-register for remote calls
		log.info("De-registering...");

		try
		{
			LocateRegistry.getRegistry().unbind(id);
			log.info("De-registered successfully on " + Util.getHost());
		}
		catch(final NotBoundException ex)
		{
			log.severe(Util.traceMessage(ex));
			System.exit(1);
		}
		catch(final RemoteException ex)
		{
			log.severe(Util.traceMessage(ex));
			System.exit(1);
		}

		log.info("Exiting.");
		System.exit(0);
	}

	@Override
	public void shutdown()
	{
		running = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderWorkerControl#want()
	 */
	@Override
	public synchronized int want()
	{
		return remaining - queue.size();
	}
}
