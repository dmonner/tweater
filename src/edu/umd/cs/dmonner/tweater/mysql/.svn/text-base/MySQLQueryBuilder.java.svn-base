package edu.umd.cs.dmonner.tweater.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import edu.umd.cs.dmonner.tweater.QueryBuilder;
import edu.umd.cs.dmonner.tweater.QueryFollow;
import edu.umd.cs.dmonner.tweater.QueryItemTime;
import edu.umd.cs.dmonner.tweater.QueryPhrase;
import edu.umd.cs.dmonner.tweater.QueryTrack;
import edu.umd.cs.dmonner.tweater.util.NumberSet;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * Reads query items from a MySQL database.
 * 
 * @author dmonner
 */
public class MySQLQueryBuilder extends QueryBuilder
{
	/**
	 * The MySQL connection pool
	 */
	private final DataSource ds;
	/**
	 * The SQL WHERE string that specifies query groups
	 */
	private final String where;
	/**
	 * The maximum number of tries to try a transaction before giving up
	 */
	private final int maxtries = 5;

	/**
	 * @param id
	 *          The log file identifier
	 * @param props
	 *          The TwEater configuration properties
	 * @param ds
	 *          The data source object pointing to the MySQL connection pool
	 */
	public MySQLQueryBuilder(final String id, final Properties props, final DataSource ds)
	{
		super(id, props);
		this.ds = ds;

		final String querygroups = props.getProperty("tweater.mysql.queryGroups");
		if(querygroups != null && !querygroups.trim().isEmpty())
		{
			where = " WHERE " + new NumberSet(querygroups).toSQL("query_group_no");
		}
		else
		{
			where = "";
		}

		final List<QueryItemTime> all = update();
		if(all != null)
		{
			set(all);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.QueryBuilder#update()
	 */
	@Override
	public List<QueryItemTime> update()
	{
		log.fine("Beginning MySQLQueryBuilder update...");
		final List<QueryItemTime> all = new LinkedList<QueryItemTime>();
		boolean querySucceeded = false;

		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		int tries = 0;

		try
		{
			// build a new tree of all query items
			while(conn == null && tries++ < maxtries)
			{
				conn = ds.getConnection();
			}

			if(conn == null)
			{
				return null;
			}

			stmt = conn.createStatement();

			// get the track queries
			rs = stmt
					.executeQuery("SELECT query_group_no, query_track_no, query_track_string, query_start_time, query_end_time "
							+ "FROM query_group INNER JOIN query_track USING (query_group_no)" + where + ";");

			while(rs.next())
			{
				all.add(new QueryItemTime(new QueryTrack(rs.getInt("query_group_no"), rs
						.getInt("query_track_no"), rs.getString("query_track_string")), rs
						.getLong("query_start_time"), rs.getLong("query_end_time")));
			}

			// get the phrase queries
			rs = stmt
					.executeQuery("SELECT query_group_no, query_phrase_no, query_phrase_string, query_start_time, query_end_time "
							+ "FROM query_group INNER JOIN query_phrase USING (query_group_no)" + where + ";");

			while(rs.next())
			{
				all.add(new QueryItemTime(new QueryPhrase(rs.getInt("query_group_no"), rs
						.getInt("query_phrase_no"), rs.getString("query_phrase_string")), rs
						.getLong("query_start_time"), rs.getLong("query_end_time")));
			}

			// get the follow queries
			rs = stmt
					.executeQuery("SELECT query_group_no, query_follow_no, query_user_id, query_start_time, query_end_time "
							+ "FROM query_group INNER JOIN query_follow USING (query_group_no)" + where + ";");

			while(rs.next())
			{
				all.add(new QueryItemTime(new QueryFollow(rs.getInt("query_group_no"), rs
						.getInt("query_follow_no"), rs.getInt("query_user_id")),
						rs.getLong("query_start_time"), rs.getLong("query_end_time")));
			}

			querySucceeded = true;
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
				if(rs != null)
				{
					rs.close();
				}
			}
			catch(final SQLException ex)
			{
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
				}
				finally
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
					}
				}
			}
		}

		if(querySucceeded)
		{
			log.fine("Completed MySQLQueryBuilder update.");
			return all;
		}
		else
		{
			log.warning("MySQLQueryBuilder update FAILED!");
			return null;
		}
	}
}
