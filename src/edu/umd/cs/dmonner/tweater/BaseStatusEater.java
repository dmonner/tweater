package edu.umd.cs.dmonner.tweater;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import edu.umd.cs.dmonner.tweater.util.Util;

import twitter4j.Status;

/**
 * Base implementation of a <code>StatusEater</code> that maintains a set of <code>QueryItem</code>s
 * that it cares about. It implements basic methods for adding to and deleting from this set, and
 * implements <code>process</code> to find all <code>QueryItem</code>s associated with a given
 * <code>Status<code>. Leaves the <code>persist</code> method for implementation by subclasses
 * focusing on a specific long-term storage system.
 * 
 * @author dmonner
 * 
 */
public abstract class BaseStatusEater implements StatusEater
{
	/**
	 * The log to which this class writes error messages.
	 */
	protected final Logger log;
	/**
	 * The set of <code>QueryItem</code>s that this <code>StatusEater</code> cares about.
	 */
	protected final Set<QueryItem> items;
	/**
	 * The number of milliseconds before an HTTP connection is considered to have timed out.
	 */
	private final int MS_CONN_TIMEOUT = 10 * 1000;

	/**
	 * @param id
	 *          The identifier of the log file that this class should use.
	 */
	public BaseStatusEater(final String id)
	{
		this.log = Logger.getLogger(id);
		this.items = new CopyOnWriteArraySet<QueryItem>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#addItem(tweater.QueryItem)
	 */
	public void addItem(final QueryItem item)
	{
		items.add(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#addItems(java.util.Collection)
	 */
	public void addItems(final Collection<? extends QueryItem> items)
	{
		this.items.addAll(items);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#clearItems()
	 */
	public void clearItems()
	{
		items.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#delItem(tweater.QueryItem)
	 */
	public void delItem(final QueryItem item)
	{
		items.remove(item);
	}

	/**
	 * A utility method that takes any URL and follows all redirects recursively until the true target
	 * is reached. Useful for finding the true destination of URL-shortened links.
	 * 
	 * @param urlStr
	 *          A URL, possibly shortened.
	 * @return The true destination URL.
	 */
	protected String expand(final String urlStr)
	{
		URLConnection conn = null;

		try
		{
			// to expand the URL, just open a connection and follow it!
			final URL inputURL = new URL(urlStr);
			conn = inputURL.openConnection();
			conn.setConnectTimeout(MS_CONN_TIMEOUT);
			conn.setReadTimeout(MS_CONN_TIMEOUT);

			// this is necessary to update the header fields and can lead to
			// StringIndexOutOfBoundsException if bad page returned
			conn.getHeaderFields();

			return conn.getURL().toString();
		}
		catch(final SocketTimeoutException ex)
		{
			log.info("Socket timeout when trying to access " + urlStr);
		}
		catch(final NullPointerException ex)
		{
			log.info("Null URL");
		}
		catch(final StringIndexOutOfBoundsException ex)
		{
			String badURL = urlStr;

			try
			{
				badURL = conn.getURL().toString();
			}
			catch(final Exception ex2)
			{
				log.severe(Util.traceMessage(ex2));
			}

			log.info("Header issue with URL: " + badURL);
		}
		catch(final IllegalArgumentException ex)
		{
			if(ex.getMessage().contains("host = null"))
			{
				log.info("Null hostname in URL.");
			}
			else
			{
				log.info("URL Error: " + ex.getMessage());
			}
		}
		catch(final MalformedURLException ex)
		{
			log.info("Invalid URL: " + urlStr);
		}
		catch(final IOException ex)
		{
			log.info("Cannot connect to URL: " + urlStr);
		}

		return urlStr;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#getQuery()
	 */
	public Set<QueryItem> getQuery()
	{
		return new TreeSet<QueryItem>(items);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.StatusEater#process(twitter4j.Status)
	 */
	@Override
	public void process(final Status status)
	{
		log.finest("Entering process() for status id " + status.getId());

		// create a new MatchableStatus, optimized for matching
		final MatchableStatus mstat = new MatchableStatus(status);

		// generate a list of QueryItems that match the status
		final List<QueryItem> matched = new LinkedList<QueryItem>();
		for(final QueryItem item : items)
			if(item.matches(mstat))
				matched.add(item);

		// if there are any matches, persist the status
		if(!matched.isEmpty())
			persist(matched, status);
		else
			log.fine("Skipping persist() because there are not QueryItem matches for status id "
				+ status.getId() + ".");
	}
}
