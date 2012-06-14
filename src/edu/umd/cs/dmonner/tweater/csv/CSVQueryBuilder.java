package edu.umd.cs.dmonner.tweater.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.mdimension.jchronic.Chronic;

import edu.umd.cs.dmonner.tweater.QueryBuilder;
import edu.umd.cs.dmonner.tweater.QueryFollow;
import edu.umd.cs.dmonner.tweater.QueryItem;
import edu.umd.cs.dmonner.tweater.QueryItemTime;
import edu.umd.cs.dmonner.tweater.QueryPhrase;
import edu.umd.cs.dmonner.tweater.QueryTrack;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * Reads query items from a CSV file.
 * 
 * @author dmonner
 */
public class CSVQueryBuilder extends QueryBuilder
{
	/**
	 * The file from which to read the query
	 */
	private final File infile;

	/**
	 * @param id
	 *          The log file identifier
	 * @param props
	 *          The TwEater configuration properties
	 */
	public CSVQueryBuilder(final String id, final Properties props)
	{
		super(id, props);
		this.infile = new File(props.getProperty("tweater.csv.infile"));

		final List<QueryItemTime> all = update();
		if(all != null)
			set(all);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.QueryBuilder#update()
	 */
	@Override
	public List<QueryItemTime> update()
	{
		log.fine("Beginning CSVQueryBuilder update; reading file " + infile + "...");

		final List<QueryItemTime> all = new LinkedList<QueryItemTime>();

		BufferedReader in = null;

		try
		{
			in = new BufferedReader(new FileReader(infile));

			String line = null;
			int lineno = 0;

			while((line = in.readLine()) != null)
			{
				lineno++;
				line = line.trim();

				// skip comment lines
				if(line.startsWith("#") || line.isEmpty())
					continue;

				final String[] fields = Util.splitUnlessQuoted(line, ",", "\"");

				if(fields.length != 4)
				{
					log.warning("Malformed input! Expected 4 fields, found " + fields.length
							+ ", line number " + lineno);
					continue;
				}

				final long start = Chronic.parse(Util.unquote(fields[0].trim())).getBegin() * 1000L;
				final long end = Chronic.parse(Util.unquote(fields[1].trim())).getEnd() * 1000L;
				final String type = Util.unquote(fields[2].trim());
				final String item = Util.unquote(fields[3].trim());

				QueryItem qitem = null;

				if(type.equalsIgnoreCase("phrase"))
					qitem = new QueryPhrase(lineno, lineno, item);
				else if(type.equalsIgnoreCase("keywords") || type.equalsIgnoreCase("keyword")
						|| type.equalsIgnoreCase("track"))
					qitem = new QueryTrack(lineno, lineno, item);
				else if(type.equalsIgnoreCase("user") || type.equalsIgnoreCase("follow"))
				{
					try
					{
						qitem = new QueryFollow(lineno, lineno, Integer.parseInt(item));
					}
					catch(final NumberFormatException ex)
					{
						log.warning("Malformed input! Expected a user id number (not \"" + item
								+ "\") on line number " + lineno);
					}
				}
				else
				{
					log.warning("Malformed input! Type of query must be \"phrase\", \"keywords\", or "
							+ "\"user\" at line number " + lineno);
				}

				if(qitem != null)
					all.add(new QueryItemTime(qitem, start, end));
			}

			log.fine("Completed CSVQueryBuilder update.");
			return all;
		}
		catch(final IOException ex)
		{
			log.severe("Problem reading input file \"" + infile.getPath() + "\":\n"
					+ Util.traceMessage(ex));
		}
		finally
		{
			if(in != null)
			{
				try
				{
					in.close();
				}
				catch(final IOException ex)
				{
				}
			}
		}

		log.warning("CSVQueryBuilder update FAILED from file " + infile + "!");
		return null;
	}
}
