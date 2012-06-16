package edu.umd.cs.dmonner.tweater.finder;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import snaq.db.DBPoolDataSource;
import twitter4j.Status;
import edu.umd.cs.dmonner.tweater.QueryBuilder;
import edu.umd.cs.dmonner.tweater.StatusEater;
import edu.umd.cs.dmonner.tweater.TwEater;
import edu.umd.cs.dmonner.tweater.csv.CSVQueryBuilder;
import edu.umd.cs.dmonner.tweater.csv.CSVStatusEater;
import edu.umd.cs.dmonner.tweater.mysql.MySQLQueryBuilder;
import edu.umd.cs.dmonner.tweater.mysql.MySQLStatusEater;
import edu.umd.cs.dmonner.tweater.util.OneLineFormatter;
import edu.umd.cs.dmonner.tweater.util.Properties;
import edu.umd.cs.dmonner.tweater.util.Util;

/**
 * Controls and directs a search, possibly across many worker machines, for full information about a
 * list of statuses whose IDs are known (generally as the result of the <code>filler</code> script
 * or some other tool that identifies relevant status IDs). Manages the global search queue, farms
 * out status searchers to <code>FinderWorker</code> processes, and, upon receiving complete
 * statuses from these workers, persists them using the associated <code>StatusEater</code>
 * instance.
 * 
 * @author dmonner
 */
public class Finder extends Thread implements FinderControl
{
	/**
	 * The log identifier for this class; static because there need only ever be a single Finder
	 * instance.
	 */
	public static final String id = "finder";

	/**
	 * Allows the starting of, or direct command-line interaction with, the Finder process.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception
	{
		final String usage = "USAGE:\n" + //
				"finder start <file.properties> <machines.cfg>\n" + //
				"                           // start a new Finder instance\n" + //
				"finder add <ids.txt>       // adds a list of tweet IDs to be processed\n" + //
				"finder size                // print the number of IDs remaining\n" + //
				"finder dump                // write all remaining IDs to stdout\n" + //
				"finder stop                // stop the existing Finder instance\n";

		// If given no command, print the usage message
		if(args.length < 1)
		{
			System.out.print(usage);
		}
		// If told to start, kick off a new Finder process
		else if(args[0].equalsIgnoreCase("start") && args.length > 2)
		{
			final String propfile = args[1];
			final String machfile = args[2];

			int port = 23891;
			if(args.length > 3)
				port = Integer.parseInt(args[3]);

			new Finder(propfile, machfile, port).start();
		}
		// If told to add, read a file full of status IDs and submit them to the Finder process
		else if(args[0].equalsIgnoreCase("add") && args.length > 1)
		{
			final String idfile = args[1];

			final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(Util.getHost())
					.lookup(id);
			final List<Long> ids = readIDFile(idfile);
			remote.add(ids);
			System.out.println("Successfully added " + ids.size() + " Tweet IDs.");
		}
		// If asked for size, print the current queue size and number of outstanding requeusts
		else if(args[0].equalsIgnoreCase("size"))
		{
			final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(Util.getHost())
					.lookup(id);
			System.out.println("Queue Size: " + remote.size());
			System.out.println("Outstanding Requests: " + remote.sent());
		}
		// If asked to dump, print a list of all status IDs remaining, including outstanding requests
		else if(args[0].equalsIgnoreCase("dump"))
		{
			final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(Util.getHost())
					.lookup(id);
			for(final Long id : remote.dump())
				System.out.println(id);
		}
		// If told to stop, shut down the Finder and all FinderWorkers it's in contact with
		else if(args[0].equalsIgnoreCase("stop"))
		{
			final FinderControl remote = (FinderControl) LocateRegistry.getRegistry(Util.getHost())
					.lookup(id);
			remote.shutdown();
			System.out.println("Shutting down the finder.");
		}
		// Otherwise, the command is invalid; print the usage message
		else
		{
			System.out.print(usage);
		}
	}

	/**
	 * Read a file containing status IDs as long integers, one per line.
	 * 
	 * @param filename
	 * @return The contents of the file as a list of status IDs.
	 */
	private static List<Long> readIDFile(final String filename)
	{
		final List<Long> ids = new LinkedList<Long>();

		try
		{
			final BufferedReader br = new BufferedReader(new FileReader(filename));

			String line = null;
			while((line = br.readLine()) != null)
			{
				line = line.trim();
				// Ignore lines starting with # as comments
				if(!line.startsWith("#"))
				{
					try
					{
						ids.add(Long.parseLong(line));
					}
					catch(final NumberFormatException ex)
					{
						// Show an error message if we can't parse a given status ID
						System.out.println("Not a valid tweet id: " + line);
					}
				}

			}
		}
		catch(final FileNotFoundException ex)
		{
			ex.printStackTrace();
		}
		catch(final IOException ex)
		{
			ex.printStackTrace();
		}

		return ids;
	}

	/**
	 * The configuration properties of this Finder instance
	 */
	public final Properties prop;
	/**
	 * Creates queries to match against so we can populate query match information
	 */
	public final QueryBuilder builder;
	/**
	 * Processes statuses we find
	 */
	public final StatusEater eater;
	/**
	 * The log file
	 */
	public final Logger log;
	/**
	 * The hosts on which FinderWorker instances may reside
	 */
	private final List<String> hosts;
	/**
	 * The queue of IDs that still need to be offloaded on to workers
	 */
	private final List<Long> idqueue;
	/**
	 * The set of IDs that we are waiting for workers to process
	 */
	private final Set<Long> sent;
	/**
	 * Whether or not this Finder instance should still be processing statuses
	 */
	private boolean running;

	/**
	 * @param propfile
	 *          The file containing the TwEater properties to use for this Finder instance
	 * @param machfile
	 *          A CSV file containing information about the worker hosts
	 * @param port
	 *          The port the Finder should expose itself on for remote calls
	 * @throws Exception
	 */
	public Finder(final String propfile, final String machfile, final int port) throws Exception
	{
		// Load the properties file
		this.running = true;
		this.prop = TwEater.loadPropertiesFile(propfile);

		// Configure Logging
		final String host = Util.getHost();
		this.log = Logger.getLogger(id);
		final Handler filehandler = new FileHandler(id + "_" + host + ".log", 100 * Util.MB, 1);
		filehandler.setFormatter(new OneLineFormatter());
		this.log.addHandler(filehandler);
		this.log.setLevel(Level.parse(prop.getProperty("tweater.logging.level")));
		this.log.info("Initializing " + id);

		// Register to allow remote access
		final FinderControl stub = (FinderControl) UnicastRemoteObject.exportObject(this, port);
		LocateRegistry.getRegistry().rebind(id, stub);
		log.info("Registered " + id + " successfully on " + host + ", port " + port);

		// Initialize the database, depending on the format
		final String dbtype = prop.getProperty("tweater.dbType").toLowerCase();

		if(dbtype.equals("mysql"))
		{
			final String driverclass = "com.mysql.jdbc.Driver";
			DriverManager.registerDriver((Driver) Class.forName(driverclass).newInstance());
			final DBPoolDataSource ds = new DBPoolDataSource();
			ds.setDriverClassName(driverclass);
			ds.setUrl("jdbc:" + dbtype + "://" + prop.getProperty("tweater.mysql.host") + "/"
					+ prop.getProperty("tweater.mysql.name") + "?continueBatchOnError=false");
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
			throw new IllegalArgumentException("Database type \"" + dbtype + "\" not supported.");
		}

		// Start the query builder
		this.builder.start();

		// Read the file describing external worker machines
		this.hosts = readMachineFile(machfile);

		// Set up the status ID queue and the set of sent IDs
		this.idqueue = new LinkedList<Long>();
		this.sent = new HashSet<Long>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#add(java.util.List)
	 */
	@Override
	public void add(final List<Long> ids)
	{
		synchronized(idqueue)
		{
			idqueue.addAll(ids);
			sent.removeAll(ids);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#dump()
	 */
	@Override
	public List<Long> dump()
	{
		synchronized(idqueue)
		{
			synchronized(sent)
			{
				final List<Long> list = new ArrayList<Long>(idqueue.size() + sent.size());
				list.addAll(idqueue);
				list.addAll(sent);
				return list;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#ignore(long)
	 */
	@Override
	public void ignore(final long status_id)
	{
		log.finer("Instructed by worker to ignore status id " + status_id + " because of null result.");
		sent.remove(status_id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#process(twitter4j.Status)
	 */
	@Override
	public void process(final Status status)
	{
		log.finest("Received status id " + status.getId() + " from worker to process.");

		// Remove the status id from the list of outstanding requests
		sent.remove(status.getId());

		// Read the status time and set up the query phrase environment appropriately so we can collect
		// the correct QueryItem match information for potential long-term storage
		eater.clearItems();
		eater.addItems(builder.at(status.getCreatedAt().getTime()));

		// Process the status
		eater.process(status);
	}

	/**
	 * @param filename
	 *          A CSV file describing worker machines
	 * @return A list of hostnames on which to look for FinderWorker processes
	 */
	private List<String> readMachineFile(final String filename)
	{
		final List<String> machines = new LinkedList<String>();

		try
		{
			final BufferedReader br = new BufferedReader(new FileReader(filename));

			String line = null;
			while((line = br.readLine()) != null)
			{
				line = line.trim();
				// Ignore comment lines
				if(!line.startsWith("#"))
				{
					// Read only the first field of the CSV file (the hostname)
					final int comma = line.indexOf(",");
					if(comma < 0)
						machines.add(line);
					else
						machines.add(line.substring(0, comma));
				}
			}
		}
		catch(final FileNotFoundException ex)
		{
			log.severe(Util.traceMessage(ex));
			machines.add(Util.getHost());
		}
		catch(final IOException ex)
		{
			log.severe(Util.traceMessage(ex));
		}

		return machines;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run()
	{
		// Loop until we're told to stop, assigning jobs to workers in each loop
		while(running)
		{
			// For each worker host
			for(final String host : hosts)
			{
				if(!idqueue.isEmpty())
				{
					// Put together a list of status IDs for them to process
					final List<Long> toSend = new LinkedList<Long>();
					try
					{
						log.finest("Attempting to contact " + host + ".");

						// Find the worker remotely
						final FinderWorkerControl remote = (FinderWorkerControl) LocateRegistry.getRegistry(
								host).lookup(FinderWorker.id);

						// See how many status IDs they want
						final int want = remote.want();

						if(want > 0)
							log.fine("Gathering statuses to send to " + host + ".");

						// While there are statuses to look up the worker wants more, add them to the list
						synchronized(idqueue)
						{
							while(!idqueue.isEmpty() && toSend.size() < want)
							{
								final long status_id = idqueue.remove(0);
								boolean skip = false;

								// Filter out statuses we already have by checking MySQL; this is to minimize
								// the # of requests to Twitter, and thus the runtime for finding a set
								if(eater instanceof MySQLStatusEater)
									skip = ((MySQLStatusEater) eater).has(status_id);

								if(!skip)
									toSend.add(status_id);
								else
									log.finer("Skipping status id " + status_id + ".");
							}
						}

						// If there are status IDs to send to the worker, send them.
						if(toSend.size() > 0)
						{
							log.info("Sending " + toSend.size() + " requests to " + host + ".");
							remote.enqueue(toSend);
							sent.addAll(toSend);
						}
					}
					catch(final ConnectException ex)
					{
						synchronized(idqueue)
						{
							idqueue.addAll(toSend);
						}
						log.warning("Connection failed to " + host + "; no worker running?");
						log.fine(Util.traceMessage(ex));
					}
					catch(final NotBoundException ex)
					{
						synchronized(idqueue)
						{
							idqueue.addAll(toSend);
						}
						log.warning("No Worker running on " + host);
						log.fine(Util.traceMessage(ex));
					}
					catch(final RemoteException ex)
					{
						synchronized(idqueue)
						{
							idqueue.addAll(toSend);
						}
						log.severe(Util.traceMessage(ex));
						log.fine(Util.traceMessage(ex));
					}
				}
			}

			try
			{
				Thread.sleep((int) (2000.0 + Math.random() * 1000.0));
			}
			catch(final InterruptedException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}

		// After we've been told to shut down, also shut down all workers we know about
		log.info("Shutting down workers...");

		for(final String host : hosts)
		{
			try
			{
				final FinderWorkerControl remote = (FinderWorkerControl) LocateRegistry.getRegistry(host)
						.lookup(FinderWorker.id);
				remote.shutdown();
				log.info("Shutdown worker on " + host);
			}
			catch(final NotBoundException ex)
			{
				log.warning("No Worker running on " + host);
			}
			catch(final RemoteException ex)
			{
				log.severe(Util.traceMessage(ex));
			}
		}

		// De-register self for remote calls
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#sent()
	 */
	@Override
	public int sent()
	{
		synchronized(sent)
		{
			return sent.size();
		}
	}

	@Override
	public void shutdown()
	{
		running = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.umd.cs.dmonner.tweater.finder.FinderControl#size()
	 */
	@Override
	public int size()
	{
		synchronized(idqueue)
		{
			return idqueue.size();
		}
	}
}
