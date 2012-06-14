package edu.umd.cs.dmonner.tweater.csv;

import java.io.PrintWriter;
import java.util.List;

import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.User;
import edu.umd.cs.dmonner.tweater.BaseStatusEater;
import edu.umd.cs.dmonner.tweater.QueryItem;
import edu.umd.cs.dmonner.tweater.util.SentimentAnalyzer;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * This class persists statuses to a local CSV file.
 * 
 * @author dmonner
 */
public class CSVStatusEater extends BaseStatusEater
{
	/**
	 * The handle on the output file
	 */
	private final PrintWriter outfile;
	/**
	 * A utility for analyzing sentiment
	 */
	private final SentimentAnalyzer analyzer;

	/**
	 * @param id
	 *          The identifier of the log file to use
	 * @param outfile
	 *          A handle on the output file
	 */
	public CSVStatusEater(final String id, final PrintWriter outfile)
	{
		super(id);

		this.outfile = outfile;
		this.analyzer = Util.getSentimentAnalyzer();

		// print the column headers to the output file
		this.outfile.println("user_id, " + //
				"user_name," + //
				"user_location," + //
				"user_followers," + //
				"user_friends," + //
				"status_id," + //
				"status_date," + //
				"status_text," + //
				"status_sentiment," + //
				"status_is_retweet," + //
				"status_retweet_of," + //
				"status_retweet_count," + //
				"status_latitude," + //
				"status_longitude," + //
				"user_join_date," + //
				"user_status_count," + //
				"user_listed," + //
				"user_verified," + //
				"user_lang," + //
				"user_utc_offset," + //
				"matched");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.StatusEater#persist(java.util.List, twitter4j.Status)
	 */
	@Override
	public void persist(final List<QueryItem> matches, final Status status)
	{
		final User user = status.getUser();

		// get sentiment information
		final double sentiment = analyzer.process(status.getText());

		// get location information
		final GeoLocation loc = status.getGeoLocation();
		final double lat = loc == null ? 0D : loc.getLatitude();
		final double lon = loc == null ? 0D : loc.getLongitude();
		final String locStr = user.getLocation() == null ? "" : scrub(user.getLocation());
		final String lang = user.getLang() == null ? "" : scrub(user.getLang());

		// get retweet information
		final boolean rt = status.isRetweet();
		final long rtct = rt ? status.getRetweetCount() : 0;
		final long rtid = rt ? status.getRetweetedStatus().getId() : -1;

		synchronized(outfile)
		{
			this.outfile.println(user.getId() + ",\"" + //
					user.getScreenName() + "\",\"" + //
					locStr + "\"," + //
					user.getFollowersCount() + "," + //
					user.getFriendsCount() + "," + //
					status.getId() + "," + //
					status.getCreatedAt() + ",\"" + //
					scrub(status.getText()) + "\"," + //
					sentiment + "," + //
					rt + "," + //
					rtid + "," + //
					rtct + "," + //
					lat + "," + //
					lon + "," + //
					user.getCreatedAt() + "," + //
					user.getStatusesCount() + "," + //
					user.getListedCount() + "," + //
					user.isVerified() + ",\"" + //
					lang + "\"," + //
					user.getUtcOffset() / 3600 + ",\"" + //
					matches.get(0).toString() + "\",");

			this.outfile.flush();
		}
	}

	/**
	 * Sanitize the input string for use in a single field in a CSV file by converting newlines to
	 * spaces and replacing double quotes with single quotes.
	 * 
	 * @param in
	 * @return The input string, sanitized for a CSV file.
	 */
	public String scrub(final String in)
	{
		return in.replaceAll("\n", " ").replaceAll("\"", "'");
	}
}
