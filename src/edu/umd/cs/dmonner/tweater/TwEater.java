package edu.umd.cs.dmonner.tweater;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import snaq.db.DBPoolDataSource;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.http.AccessToken;
import edu.umd.cs.dmonner.tweater.csv.CSVQueryBuilder;
import edu.umd.cs.dmonner.tweater.csv.CSVStatusEater;
import edu.umd.cs.dmonner.tweater.mysql.MySQLQueryBuilder;
import edu.umd.cs.dmonner.tweater.mysql.MySQLStatusEater;
import edu.umd.cs.dmonner.tweater.util.AlertEmailer;
import edu.umd.cs.dmonner.tweater.util.OneLineFormatter;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

public class TwEater extends Thread implements TwEaterControl
{
	/**
	 * @return A new property list containing defaults
	 */
	public static Properties getDefaultProperties()
	{
		final Properties prop = new Properties();

		// -- Load all property defaults, where applicable

		prop.setProperty("tweater.dbType", "csv", //
				"Type of persistent storage to use; valid values are: csv, mysql");
		prop.setProperty("tweater.logging.level", "INFO", //
				"Granularity of log messages; valid values (from rarest to most frequent) are: " + //
						"SEVERE, WARNING, INFO, FINE, FINER, FINEST");
		prop.setProperty("tweater.logging.maxLogSizeMB", "10", //
				"Maximum size of a TwEater log file.");
		prop.setProperty("tweater.logging.maxLogs", "10", //
				"Maximum number of Tweater log files. After this many logs reach the maximum size, " + //
						"the first file will be overwritten. To avoid this, set a large maxLogs and/or " + //
						"maxLogSizeMB, or turn down your logging level. Beware filling your disk!");

		// Properties specific to CSV configuration

		prop.setProperty("tweater.csv.infile", "query.csv", //
				"CSV file specifying Twitter query");
		prop.setProperty("tweater.csv.outfile", "results.csv", //
				"CSV file to which to write the received tweet data");

		// Properties specific to MySQL configuration

		prop.setProperty("tweater.mysql.minConnections", "3", //
				"Minimum number of database connections to maintain; for performance tuning");
		prop.setProperty("tweater.mysql.coreConnections", "30", //
				"Base number of database connections to maintain; for performance tuning");
		prop.setProperty("tweater.mysql.maxConnections", "50", //
				"Minimum number of database connections allowed; for performance tuning");
		prop.setProperty("tweater.mysql.idleTimeout", "120", //
				"Idle timeout on database connection (in seconds); for performance tuning");
		prop.setProperty("tweater.mysql.queryGroups", "", //
				"Subset of query group numbers to include in query; leave blank for all");
		prop.setProperty("tweater.mysql.host", "", //
				"Hostname of the MySQL database server");
		prop.setProperty("tweater.mysql.name", "", //
				"Name of the MySQL database");
		prop.setProperty("tweater.mysql.user", "", //
				"Username for the MySQL database");
		prop.setProperty("tweater.mysql.pass", "", //
				"Password for the MySQL database");

		// Properties specific to email notifications

		prop.setProperty("tweater.mail.smtp", "", //
				"SMTP server from which to send email alerts");
		prop.setProperty("tweater.mail.to", "", //
				"Email address to which to send email alerts");
		prop.setProperty("tweater.mail.from", "", //
				"Email address from which email alerts will originate");

		// Properties of the QueryBuilder

		prop.setProperty("tweater.builder.interval", "180", //
				"Interval (in seconds) between QueryBuilder trips to the data source");

		// Properties of the StatusEater

		prop.setProperty("tweater.eater.useSentimentAnalysis", "true", //
				"Sentiment-analyze tweets as they come in; incurs additional memory overhead");

		// Properties of the tweet queue in StatusServer

		prop.setProperty("tweater.queue.coreThreads", "50", //
				"Base number of status-processing threads; for performance tuning");
		prop.setProperty("tweater.queue.maxThreads", "50", //
				"Maximum number of status-processing threads; for performance tuning");
		prop.setProperty("tweater.queue.idleTimeout", "60", //
				"Idle timeout on processing threads; for performance tuning");
		prop.setProperty("tweater.queue.rejectionMessageInterval", "600", //
				"Interval (in seconds) between log messages about rejected statuses");
		prop.setProperty("tweater.queue.statusEmailHour", "6", // 6 am
				"Interval (in seconds) between email messages about rejected statuses");
		prop.setProperty("tweater.queue.statusEmailInterval", "43200", // 12 hours
				"Interval (in seconds) between email messages about rejected statuses");
		prop.setProperty("tweater.queue.rejectionEmailInterval", "3600", //
				"Interval (in seconds) between email messages about rejected statuses");
		prop.setProperty("tweater.queue.rejectionEmailInterval", "3600", //
				"Interval (in seconds) between email messages about rejected statuses");
		prop.setProperty("tweater.queue.trackLimitMessageInterval", "600", //
				"Interval (in seconds) between log messages about track limitations");
		prop.setProperty("tweater.queue.resourceLimitMessageInterval", "600", //
				"Interval (in seconds) between log messages about resource limitations");
		prop.setProperty("tweater.queue.resourceLimitEmailInterval", "3600", //
				"Interval (in seconds) between email messages about resource limitations");
		prop.setProperty("tweater.queue.resourceLimitMessageThreshold", "0.50", //
				"Fraction of memory use above which to log resource limitations");
		prop.setProperty("tweater.queue.resourceLimitEmailThreshold", "0.80", //
				"Fraction of memory use above which to send email alerts about resource limitations");
		prop.setProperty("tweater.queue.resourceLimitRejectionThreshold", "0.95", //
				"Fraction of memory use above which to reject new statuses");

		// Properties of Twitter OAuth

		prop.setProperty("oauth.accessToken", "", //
				"Your Access Token from Twitter for use with TwEater");
		prop.setProperty("oauth.accessTokenSecret", "", //
				"Your Access Token Secret from Twitter for use with TwEater");
		prop.setProperty("oauth.consumerKey", "RTojEz16nwhI3IrBrZpNQ", //
				"TwEater's Consumer Token Key");
		prop.setProperty("oauth.consumerSecret", "lNfVdu2cFKrlEbaw1OiM2Y3TgVKLGBI3AuEEblZilek", //
				"TwEater's Consumer Token Secret");

		return prop;
	}

	/**
	 * Loads the default properties, replacing them as necessary with properties read from the input
	 * file.
	 * 
	 * @param propfile
	 *          The file to read
	 * @return The resulting property list
	 */
	public static Properties loadPropertiesFile(final String propfile)
	{
		final Properties prop = getDefaultProperties();

		// -- Try to load the properties file given on the command line, overwriting defaults
		try
		{
			prop.load(propfile);
		}
		catch(final FileNotFoundException ex)
		{
			System.out.println("Cannot find properties file: " + propfile);
			System.exit(1);
		}
		catch(final IOException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}

		// -- Check to make sure necessary properties have been provided in the proper ranges

		// OAuth properties

		prop.requireProperty("oauth.accessToken");
		prop.requireProperty("oauth.accessTokenSecret");
		prop.requireProperty("oauth.consumerKey");
		prop.requireProperty("oauth.consumerSecret");

		// MySQL properties

		if(prop.getProperty("tweater.dbType").equalsIgnoreCase("mysql"))
		{
			prop.requireProperty("tweater.mysql.host");
			prop.requireProperty("tweater.mysql.name");
			prop.requireProperty("tweater.mysql.user");
			prop.requireProperty("tweater.mysql.pass");
			prop.requireIntegerProperty("tweater.mysql.minConnections", 0);
			prop.requireIntegerProperty("tweater.mysql.coreConnections", 1);
			prop.requireIntegerProperty("tweater.mysql.maxConnections", 1);
			prop.requireIntegerProperty("tweater.mysql.idleTimeout", 0);
		}

		// StatusQueue properties

		prop.requireIntegerProperty("tweater.queue.coreThreads", 1);
		prop.requireIntegerProperty("tweater.queue.maxThreads", 1);
		prop.requireIntegerProperty("tweater.queue.idleTimeout", 0);
		prop.requireIntegerProperty("tweater.queue.rejectionMessageInterval", -1);
		prop.requireIntegerProperty("tweater.queue.trackLimitMessageInterval", -1);
		prop.requireIntegerProperty("tweater.queue.resourceLimitMessageInterval", -1);
		prop.requireIntegerProperty("tweater.queue.resourceLimitEmailInterval", -1);
		prop.requireFloatProperty("tweater.queue.resourceLimitMessageThreshold", 0.0f, 1.0f);
		prop.requireFloatProperty("tweater.queue.resourceLimitEmailThreshold", 0.0f, 1.0f);
		prop.requireFloatProperty("tweater.queue.resourceLimitRejectionThreshold", 0.0f, 1.0f);

		// -- Return finalized properties

		return prop;
	}

	/**
	 * Allows for launching and controlling TwEater instances via the command line.
	 * 
	 * @param args
	 */
	public static void main(final String[] args)
	{
		// System.setSecurityManager(new RMISecurityManager());
		final String usage = "USAGE:\n" + //
				"tweater start <file.properties>   // start a new TwEater instance\n" + //
				"tweater list                      // list all instances\n" + //
				"tweater prop                      // write default properties to stdout\n" + //
				"tweater stop <instance_number>    // shut down a running instance\n";

		// If called with no arguments, print a usage message
		if(args.length < 1)
		{
			System.out.print(usage);
		}
		// If called with "start", begin a new TwEater instance
		else if(args[0].equalsIgnoreCase("start"))
		{
			String propfile = "tweater.properties";

			if(args.length > 1)
			{
				propfile = args[1];
			}

			try
			{
				new TwEater(propfile).start();
			}
			catch(final Exception ex)
			{
				ex.printStackTrace();
			}
		}
		// If called with "list", pretty print info about running TwEater instances
		else if(args[0].equalsIgnoreCase("list"))
		{
			System.out.println(" num |        name          |    size    | collecting | mem% ");
			System.out.println("-----|----------------------|------------|------------|------");
			final String tpl = "     |                      |            |            |      ";
			final int numStart = 1;
			final int nameStart = 7;
			final int sizeStart = 30;
			final int collStart = 43;
			final int memStart = 56;

			try
			{
				int index = 0;
				for(final String name : LocateRegistry.getRegistry().list())
				{
					try
					{
						final Remote obj = LocateRegistry.getRegistry().lookup(name);
						if(obj instanceof TwEaterControl)
						{
							final TwEaterControl tw = (TwEaterControl) obj;
							final StringBuffer s = new StringBuffer(tpl.toString());
							final String numStr = String.valueOf(index);
							final String sizeStr = String.valueOf(tw.size());
							final String collStr = String.valueOf(tw.collecting());
							final String memStr = String.valueOf((int) (tw.memory() * 100)) + "%";

							s.replace(numStart, numStart + numStr.length(), numStr);
							s.replace(nameStart, nameStart + name.length(), name);
							s.replace(sizeStart, sizeStart + sizeStr.length(), sizeStr);
							s.replace(collStart, collStart + collStr.length(), collStr);
							s.replace(memStart, memStart + memStr.length(), memStr);

							System.out.println(s.toString());
							index++;
						}
					}
					catch(final NotBoundException ex)
					{
						ex.printStackTrace();
					}
					catch(final ConnectException ex)
					{
						rmiRemove(LocateRegistry.getRegistry(), name);
					}
					catch(final RemoteException ex)
					{
						ex.printStackTrace();
					}
				}
			}
			catch(final RemoteException ex)
			{
				ex.printStackTrace();
			}
		}
		// If called with "prop", generate a default TwEater configuration file
		else if(args[0].equalsIgnoreCase("prop"))
		{
			getDefaultProperties().save(System.out,
					"Default TwEater properties file, generated " + new Date());
		}
		// If called with "stop", shut down the given TwEater instance (after prompting for
		// confirmation)
		else if(args[0].equalsIgnoreCase("stop") && args.length > 1)
		{
			try
			{
				final int target = Integer.parseInt(args[1]);
				int index = 0;
				for(final String name : LocateRegistry.getRegistry().list())
				{
					try
					{
						final Remote obj = LocateRegistry.getRegistry().lookup(name);
						if(obj instanceof TwEaterControl)
						{
							if(index == target)
							{
								final TwEaterControl tw = (TwEaterControl) obj;

								if(tw.collecting())
								{
									System.out.print("Stop collection on instance " + name + "? [y|N] ");
									final char answer = (char) System.in.read();

									if(answer == 'y' || answer == 'Y')
									{
										tw.shutdown();
										System.out.println("Collection stopped on instance " + name + ".");
									}
								}
								else
								{
									System.out.println("Collection is already stopped on instance " + name + ".");
								}

								break;
							}

							index++;
						}
					}
					catch(final ConnectException ex)
					{
						rmiRemove(LocateRegistry.getRegistry(), name);
					}
					catch(final NotBoundException ex)
					{
						ex.printStackTrace();
					}
					catch(final RemoteException ex)
					{
						ex.printStackTrace();
					}
					catch(final IOException ex)
					{
						ex.printStackTrace();
					}
				}
			}
			catch(final NumberFormatException ex)
			{
				ex.printStackTrace();
			}
			catch(final RemoteException ex)
			{
				ex.printStackTrace();
			}
		}
		// If we got an argument we don't recognize, print the usage message
		else
		{
			System.out.print(usage);
		}
	}

	private static void rmiRemove(final Registry registry, final String name)
	{
		System.out.println("Failed to connect to " + name + "; removing from registry.");
		try
		{
			registry.unbind(name);
		}
		catch(final NotBoundException ex)
		{
			ex.printStackTrace();
		}
		catch(final AccessException ex)
		{
			ex.printStackTrace();
		}
		catch(final RemoteException ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * The identifier for this TwEater instance; also used as the log file name
	 */
	public final String id;
	/**
	 * The destination for urgent email alert messages
	 */
	public final AlertEmailer alert;
	/**
	 * The configuration properties of this TwEater instance
	 */
	public final Properties prop;
	/**
	 * A reference to the query builder associated with this TwEater instance
	 */
	public final QueryBuilder builder;
	/**
	 * A reference to the querier associated with this TwEater instance
	 */
	public final Querier querier;
	/**
	 * A reference to the status queue associated with this TwEater instance
	 */
	public final StatusQueue queue;
	/**
	 * The log file associated with this TwEater instance
	 */
	public final Logger log;

	/**
	 * Tells whether this TwEater instance is currently collecting data from Twitter, or shut down and
	 * merely working through its backlog
	 */
	private boolean collecting;

	public TwEater(final String propfile) throws Exception
	{
		// -- Read properties file
		prop = loadPropertiesFile(propfile);

		// -- Setup up a Twitter stream and its OAuth info
		final TwitterStream tw = new TwitterStreamFactory().getInstance();
		loadConsumerToken(tw);
		loadAccessToken(tw);

		alert = new AlertEmailer(prop);
		id = "tweater" + new Date().getTime();
		collecting = true;

		// -- Initial logging configuration
		log = Logger.getLogger(id);
		final int maxLogSizeMB = prop.getIntegerProperty("tweater.logging.maxLogSizeMB");
		final int maxLogs = prop.getIntegerProperty("tweater.logging.maxLogs");
		final Handler filehandler = new FileHandler(id + ".log", maxLogSizeMB * Util.MB, maxLogs);
		filehandler.setFormatter(new OneLineFormatter());
		log.addHandler(filehandler);
		log.setLevel(Level.parse(prop.getProperty("tweater.logging.level")));
		log.info("Initializing " + id);

		final String dbtype = prop.getProperty("tweater.dbType").toLowerCase();
		final StatusEater eater;

		if(dbtype.equals("mysql"))
		{
			final String driverclass = "com.mysql.jdbc.Driver";
			DriverManager.registerDriver((Driver) Class.forName(driverclass).newInstance());
			final DBPoolDataSource ds = new DBPoolDataSource();
			ds.setDriverClassName(driverclass);
			ds.setUrl("jdbc:" + dbtype + "://" + prop.getProperty("tweater.mysql.host") + "/"
					+ prop.getProperty("tweater.mysql.name") + "?continueBatchOnError=false"
					+ "&useUnicode=true" + "&characterEncoding=utf8" + "&characterSetResults=utf8");
			ds.setUser(prop.getProperty("tweater.mysql.user"));
			ds.setPassword(prop.getProperty("tweater.mysql.pass"));
			ds.setMinPool(Integer.parseInt(prop.getProperty("tweater.mysql.minConnections")));
			ds.setMaxPool(Integer.parseInt(prop.getProperty("tweater.mysql.coreConnections")));
			ds.setMaxSize(Integer.parseInt(prop.getProperty("tweater.mysql.maxConnections")));
			ds.setIdleTimeout(Integer.parseInt(prop.getProperty("tweater.mysql.idleTimeout")));
			ds.setValidatorClassName("snaq.db.AutoCommitValidator");

			builder = new MySQLQueryBuilder(id, prop, ds);
			eater = new MySQLStatusEater(id, prop, ds);
		}
		else if(dbtype.equals("csv"))
		{
			String outfile = "results.csv";

			if(prop.containsKey("tweater.csv.outfile"))
				outfile = prop.getProperty("tweater.csv.outfile");

			builder = new CSVQueryBuilder(id, prop);
			eater = new CSVStatusEater(id, prop, new PrintWriter(new FileWriter(outfile, true), true));
		}
		else
		{
			builder = null;
			eater = null;
			log.severe("Database type \"" + dbtype + "\" not supported.");
			System.exit(1);
		}

		// start the querier
		querier = new Querier(id, tw, builder);
		queue = new StatusQueue(id, eater, prop, alert);
		querier.addQueue(queue);

		try
		{
			final TwEaterControl stub = (TwEaterControl) UnicastRemoteObject.exportObject(this, 0);
			LocateRegistry.getRegistry().rebind(id, stub);
			log.info("Registered successfully on " + Util.getHost());
		}
		catch(final RemoteException ex)
		{
			log.severe(Util.traceMessage(ex));
			System.exit(1);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.TwEaterControl#collecting()
	 */
	@Override
	public boolean collecting() throws RemoteException
	{
		return collecting;
	}

	/**
	 * Reads an Access Token from this instance's configuration and adds the token to the given
	 * Twitter stream object.
	 * 
	 * @param tw
	 */
	private void loadAccessToken(final TwitterStream tw)
	{
		AccessToken accessToken = null;

		if(prop.getProperty("oauth.accessToken") == null
				|| prop.getProperty("oauth.accessTokenSecret") == null)
		{
			log.severe("Cannot find a valid User Access Token.");
			System.exit(1);
		}
		else
		{
			accessToken = new AccessToken(prop.getProperty("oauth.accessToken"),
					prop.getProperty("oauth.accessTokenSecret"));
		}

		// get an authorized Twitter instance
		tw.setOAuthAccessToken(accessToken);
	}

	/**
	 * Reads a Consumer Token from this instance's configuration and adds the token to the given
	 * Twitter stream object.
	 * 
	 * @param tw
	 */
	private void loadConsumerToken(final TwitterStream tw)
	{
		if(prop.getProperty("oauth.consumerKey") == null
				|| prop.getProperty("oauth.consumerSecret") == null)
		{
			log.severe("Cannot find a valid Consumer Key.");
			System.exit(1);
		}

		tw.setOAuthConsumer(prop.getProperty("oauth.consumerKey"),
				prop.getProperty("oauth.consumerSecret"));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.TwEaterControl#memory()
	 */
	@Override
	public float memory()
	{
		return Util.getMemoryUtilizationPercent();
	}

	/**
	 * Starts the associated query builder, status queue, and querier threads. Waits in a loop until
	 * the TwEater instance has been shut down and the status queue has worked through its backlog.
	 */
	@Override
	public void run()
	{
		builder.start();
		queue.start();
		querier.start();

		while(collecting || !queue.finished())
		{
			if(!collecting)
			{
				log.info("Post-shutdown jobs remaining: " + queue.getQueueSize());
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

		System.out.println("All post-shutdown jobs finished.");

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

		System.out.println("Exiting.");
		System.exit(0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.TwEaterControl#shutdown()
	 */
	@Override
	public void shutdown()
	{
		queue.shutdown();
		querier.shutdown();
		builder.shutdown();
		collecting = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.TwEaterControl#size()
	 */
	@Override
	public int size()
	{
		return queue.getQueueSize();
	}
}
