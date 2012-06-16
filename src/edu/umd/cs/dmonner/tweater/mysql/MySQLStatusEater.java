package edu.umd.cs.dmonner.tweater.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import edu.umd.cs.dmonner.tweater.BaseStatusEater;
import edu.umd.cs.dmonner.tweater.QueryItem;
import edu.umd.cs.dmonner.tweater.QueryItem.Type;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.SentimentAnalyzer;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * This class persists statuses to a MySQL database.
 * 
 * @author dmonner
 */
public class MySQLStatusEater extends BaseStatusEater
{
	/**
	 * The handle to the MySQL database connection pool
	 */
	private final DataSource ds;
	/**
	 * The maximum number of times to try a transaction before giving up
	 */
	private final int MAX_TRIES = 5;
	/**
	 * A utility for analyzing sentiment
	 */
	private final SentimentAnalyzer analyzer;

	/**
	 * @param id
	 *          The identifier of the log file to use
	 * @param ds
	 *          A handle on the MySQL database connection poor
	 */
	public MySQLStatusEater(final String id, final Properties prop, final DataSource ds)
	{
		super(id);
		this.ds = ds;

		if(prop.getBooleanProperty("tweater.eater.useSentimentAnalysis"))
			this.analyzer = Util.getSentimentAnalyzer();
		else
			this.analyzer = null;
	}

	/**
	 * Obtains a database connection safely while logging errors; convenience method.
	 * 
	 * @return An open connection to the database.
	 */
	private Connection connect()
	{
		Connection conn = null;

		while(conn == null)
		{
			try
			{
				conn = ds.getConnection();

				if(conn == null)
				{
					try
					{
						Thread.sleep((int) (500 * Math.random()));
					}
					catch(final InterruptedException ex)
					{
					}
				}
			}
			catch(final SQLException ex)
			{
				log.severe( //
				"SQLState: " + ex.getSQLState() + "\n" + //
						"VendorError: " + ex.getErrorCode() + "\n" + //
						Util.traceMessage(ex));
			}
		}

		return conn;
	}

	/**
	 * Closes the passed-in connection safely while logging errors; convenience method.
	 * 
	 * @param conn
	 */
	private void disconnect(final Connection conn)
	{
		try
		{
			if(conn != null)
			{
				conn.close();
			}
		}
		catch(final SQLException ex)
		{
			log.severe( //
			"SQLState: " + ex.getSQLState() + "\n" + //
					"VendorError: " + ex.getErrorCode() + "\n" + //
					Util.traceMessage(ex));
		}
	}

	/**
	 * Runs a list of SQL statements as a single transaction over the given connection. In the even of
	 * a busy database, this method will retry the transaction up to <code>MAX_TRIES</code> times.
	 * 
	 * @param conn
	 * @param id
	 * @param sqls
	 */
	private void execute(final Connection conn, final long id, final List<String> sqls)
	{
		execute(conn, id, sqls, MAX_TRIES);
	}

	/**
	 * Runs a list of SQL statements as a single transaction over the given connection, allowing
	 * <code>tries</code> retries if the database is too busy.
	 * 
	 * @param conn
	 * @param id
	 * @param sqls
	 */
	private void execute(final Connection conn, final long id, final List<String> sqls,
			final int tries)
	{
		Statement stmt = null;
		try
		{
			// Tell the connection to wait to commit the transaction until we're done
			conn.setAutoCommit(false);
			stmt = conn.createStatement();

			// Add all the SQL statements to a batch
			for(final String sql : sqls)
			{
				stmt.addBatch(sql);
			}

			// Execute the batch
			log.finest("Executing MySQL statement batch for status id " + id + ".");
			stmt.executeBatch();

			// Commit the results, assuming we succeed
			log.finest("Committing inserts for status id " + id + ".");
			conn.commit();
			log.finest("Database inserts committed for status id " + id + ".");
		}
		catch(final SQLException ex)
		{
			// This will happen if Twitter sends us a duplicate status, as happens occasionally
			if(ex.getMessage().contains("Duplicate entry"))
			{
				// Roll back the transaction and log the duplicate error
				log.fine("Duplicate entry into database for status id " + id + "; transaction aborted.");
				log.finest(ex.getMessage() + "\nQuery = \n" + Util.listFormat(sqls) + "\nStatus id: " + id);
				rollback(conn);
			}
			// This happens when the database is too busy to process our transaction
			else if(ex.getMessage().contains("try restarting transaction"))
			{
				// We roll back what we have, if anything
				rollback(conn);

				// Error message recommends restarting the transaction/batch
				if(tries > 0)
				{
					// Try again if we have any tries left
					log.warning("Restarting transaction for status id " + id + "...");
					execute(conn, id, sqls, tries - 1);
				}
				else
				{
					// If we don't, log the transaction failure
					log.severe("Failed after several tries; aborting transaction for status id " + id + ".");
					log.severe(Util.traceMessage(ex));
				}
			}
			// This happens when the text (usually a URL) is too long for our database field
			else if(ex.getMessage().contains("Data truncation"))
			{
				// This doesn't signify a failure or error, so log as INFO
				log.info(ex.getMessage() + "\nStatus id: " + id);
				log.finest("Query = \n" + Util.listFormat(sqls) + "\nStatus id: " + id);
			}
			// This happens if people place certain crazy unicode characters in their tweets, I guess?
			else if(ex.getMessage().contains("Incorrect string value"))
			{
				// Rollback the transaction and log the failure
				rollback(conn);
				log.info(ex.getMessage() + "\nStatus id: " + id);
				log.finest("Query = \n" + Util.listFormat(sqls) + "\nStatus id: " + id);
			}
			// Otherwise, the error is of an unknown type
			else
			{
				// Log it as a severe failure
				log.severe( //
				"SQLState: " + ex.getSQLState() + "\n" + //
						"VendorError: " + ex.getErrorCode() + "\n" + //
						Util.traceMessage(ex));
				log.severe("Query = \n" + Util.listFormat(sqls) + "\nStatus id: " + id);
			}
		}
		finally
		{
			try
			{
				if(stmt != null)
				{
					stmt.close();
				}
			}
			catch(final SQLException ex)
			{
				log.severe( //
				"SQLState: " + ex.getSQLState() + "\n" + //
						"VendorError: " + ex.getErrorCode() + "\n" + //
						Util.traceMessage(ex));
				log.finest("Query = \n" + Util.listFormat(sqls) + "\nStatus id: " + id);
			}
		}
	}

	/**
	 * Searches the database for a particular status.
	 * 
	 * @param status_id
	 * @return <code>true</code> iff the database contains the status with the given ID.
	 */
	public boolean has(final long status_id)
	{
		log.finest("Checking database for status id " + status_id + ".");
		final Connection conn = connect();
		Statement stmt = null;
		boolean have = false;
		try
		{
			// Run a SELECT COUNT query to see if we have the status with the given ID
			stmt = conn.createStatement();
			final ResultSet result = stmt.executeQuery("SELECT COUNT(*) FROM status WHERE status_id = "
					+ status_id + ";");
			// If we do have it, we will have one row of results with a single int > 0
			if(result.first() && result.getInt(1) > 0)
			{
				have = true;
			}
		}
		catch(final SQLException ex)
		{
			log.severe( //
			"SQLState: " + ex.getSQLState() + "\n" + //
					"VendorError: " + ex.getErrorCode() + "\n" + //
					Util.traceMessage(ex));
		}
		finally
		{
			try
			{
				if(stmt != null)
				{
					stmt.close();
				}
			}
			catch(final SQLException ex)
			{
				log.severe( //
				"SQLState: " + ex.getSQLState() + "\n" + //
						"VendorError: " + ex.getErrorCode() + "\n" + //
						Util.traceMessage(ex));
			}

			disconnect(conn);
		}

		log.finest("Database " + (have ? "contains" : "does not contain") + " status id " + status_id
				+ ".");
		return have;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.StatusEater#persist(java.util.List, twitter4j.Status)
	 */
	@Override
	public void persist(final List<QueryItem> matches, final Status status)
	{
		log.finest("Entering persist() for status id " + status.getId());

		final User user = status.getUser();

		// get sentiment information
		final double sentiment = analyzer == null ? 0D : analyzer.process(status.getText());

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

		// get hashtags and links
		final HashtagEntity[] tags = status.getHashtagEntities();
		final URLEntity[] urls = status.getURLEntities();
		final UserMentionEntity[] mentions = status.getUserMentionEntities();

		final List<String> sqls = new LinkedList<String>();

		// SQL to insert the user
		sqls.add("INSERT INTO user(" + //
				"user_id, " + //
				"user_info_from_status, " + //
				"user_name, " + //
				"user_join_date, " + //
				"user_status_count, " + //
				"user_followers, " + //
				"user_friends, " + //
				"user_listed, " + //
				"user_verified, " + //
				"user_lang, " + //
				"user_location, " + //
				"user_utc_offset) " + //
				"VALUES (" + //
				user.getId() + ", " + //
				status.getId() + ", '" + //
				user.getScreenName() + "', " + //
				user.getCreatedAt().getTime() + ", " + //
				user.getStatusesCount() + ", " + //
				user.getFollowersCount() + ", " + //
				user.getFriendsCount() + ", " + //
				user.getListedCount() + ", " + //
				(user.isVerified() ? 1 : 0) + ", '" + //
				lang + "', '" + //
				locStr + "', "//
				+ user.getUtcOffset() + ");");

		// SQL to insert the status
		sqls.add("INSERT INTO status(" + //
				"status_id, " + //
				"user_id, " + //
				"status_date, " + //
				"status_text, " + //
				"status_sentiment, " + //
				"status_is_retweet, " + //
				"status_retweet_of, " + //
				"status_retweet_count, " + //
				"status_latitude, " + //
				"status_longitude) " + //
				" VALUES (" + //
				status.getId() + ", " + //
				user.getId() + ", " + //
				status.getCreatedAt().getTime() + ", '" + //
				scrub(status.getText()) + "', " + //
				sentiment + ", " + //
				rt + ", " + //
				rtid + ", " + //
				rtct + ", " + //
				lat + ", " + //
				lon + ");");

		// SQL to insert QueryItem matches
		for(final QueryItem match : matches)
		{
			String table = null;
			if(match.type == Type.TRACK)
				table = "track_match(query_track_no, status_id)";
			else if(match.type == Type.PHRASE)
				table = "phrase_match(query_phrase_no, status_id)";
			else if(match.type == Type.FOLLOW)
				table = "follow_match(query_follow_no, status_id)";
			else
				log.warning("Unhandled match type: " + match.type);

			if(table != null)
				sqls.add("INSERT INTO " + table + " VALUES (" + match.id + ", " + status.getId() + ");");
		}

		// SQL to insert hashtag entities
		for(final HashtagEntity tag : tags)
		{
			final String text = scrub(tag.getText()).toLowerCase();

			sqls.add("INSERT INTO hashtag(" + //
					"hashtag_text) " + //
					"VALUES ('" + //
					text + "') " + //
					"ON DUPLICATE KEY UPDATE hashtag_no = LAST_INSERT_ID(hashtag_no);");

			sqls.add("INSERT INTO hashtag_match(" + //
					"hashtag_no, " + //
					"status_id, " + //
					"hashtag_startidx, " + //
					"hashtag_endidx) " + //
					"VALUES " + //
					"(LAST_INSERT_ID(), " + //
					status.getId() + ", " + //
					tag.getStart() + ", " + //
					tag.getEnd() + ");");
		}

		// SQL to expand and insert URL entities
		for(final URLEntity url : urls)
		{
			if(url != null && url.getURL() != null)
			{
				final String u = url.getURL().toString().trim();
				if(!u.equals(""))
				{
					final String urlStr = scrub(u);
					final String expandedStr = scrub(expand(u));

					sqls.add("INSERT INTO expanded_link(" + //
							"expanded_link_url) " + //
							"VALUES ('" + //
							expandedStr + "') " + //
							"ON DUPLICATE KEY UPDATE expanded_link_no = LAST_INSERT_ID(expanded_link_no);");

					sqls.add("INSERT INTO link(" + //
							"link_url, " + //
							"expanded_link_no) " + //
							"VALUES ('" + //
							urlStr + "', " + //
							"LAST_INSERT_ID()) " + //
							"ON DUPLICATE KEY UPDATE link_no = LAST_INSERT_ID(link_no);");

					sqls.add("INSERT INTO link_match(" + //
							"link_no, " + //
							"status_id, " + //
							"link_startidx, " + //
							"link_endidx) " + //
							"VALUES " + //
							"(LAST_INSERT_ID(), " + //
							status.getId() + ", " + //
							url.getStart() + ", " + //
							url.getEnd() + ");");
				}
			}
		}

		// SQL to insert mentions
		for(final UserMentionEntity mention : mentions)
		{
			sqls.add("INSERT INTO mention(" + //
					"status_id, " + //
					"mention_startidx, " + //
					"mention_endidx, " + //
					"mention_by, " + //
					"mention_of) " + //
					"VALUES (" + //
					status.getId() + ", " + //
					mention.getStart() + ", " + //
					mention.getEnd() + ", " + //
					user.getId() + ", " + //
					mention.getId() + ");");
		}

		log.finest("Attempting " + sqls.size() + " database inserts for status id " + status.getId()
				+ ".");

		// Run the whole insert as a single batch; this greatly reduces load on the database compared to
		// individual insert commands
		final Connection conn = connect();
		execute(conn, status.getId(), sqls);
		disconnect(conn);
	}

	/**
	 * Roll back the last transaction on this connection; convenience method.
	 * 
	 * @param conn
	 */
	private void rollback(final Connection conn)
	{
		try
		{
			log.fine("Rolling back transaction because of error...");
			conn.rollback();
		}
		catch(final SQLException ex)
		{
			log.severe( //
			"SQLState: " + ex.getSQLState() + "\n" + //
					"VendorError: " + ex.getErrorCode() + "\n" + //
					Util.traceMessage(ex));
		}
	}

	/**
	 * Sanitizes a string for use as a field in a MySQL database by escaping all backslashes and
	 * single-quotes.
	 * 
	 * @param in
	 * @return The input string, sanitized for insertion into a SQL database.
	 */
	public String scrub(final String in)
	{
		// first one actually replaces \ with escaped \\, second replaces ' with \';
		// \ in regex is expressed as \\, which is java string literal is \\\\, and same applies
		// to the replacement pattern; we don't need
		return in.replaceAll("\\\\", "\\\\\\\\").replaceAll("'", "\\\\'");
	}
}
