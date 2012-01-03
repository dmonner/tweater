package edu.umd.cs.dmonner.tweater.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.http.AccessToken;
import twitter4j.http.RequestToken;

/**
 * Walks the user through the process of logging in to Twitter to obtain an access token, which is a
 * prerequisite for using the Streaming API. Also allows the user to change the consumer key of the
 * application.
 * 
 * @author dmonner
 */
public class Setup
{
	/**
	 * The file from which existing credentials are read and to which new credentials are added
	 */
	private static String propfile;
	/**
	 * The table of all properties in the propfile.
	 */
	private static Properties prop;
	/**
	 * The TwitterStream object used to create necessary Access Tokens
	 */
	private static TwitterStream tw;

	/**
	 * Checks our property list for an existing Access Token, and allows the user to either keep the
	 * current token, override an existing token, or enter a new one if none exists.
	 */
	public static void loadAccessToken()
	{
		AccessToken accessToken = null;

		final boolean missing =
			prop.getProperty("oauth.accessToken") == null
				|| prop.getProperty("oauth.accessTokenSecret") == null;
		boolean reset = false;

		if(!missing)
		{
			System.out.println("An existing Access Token was found in " + propfile);
			System.out.print("Do you want to reset the Access Token anyway? [y|N] ");
			final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

			try
			{
				final String line = br.readLine().trim();
				reset = line.startsWith("y") || line.startsWith("Y");
			}
			catch(final IOException ex)
			{
				ex.printStackTrace();
			}
		}

		if(missing || reset)
		{

			RequestToken requestToken = null;

			try
			{
				requestToken = tw.getOAuthRequestToken();
			}
			catch(final TwitterException ex)
			{
				ex.printStackTrace();
			}

			final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while(accessToken == null)
			{
				System.out.println("To create an Access Token, you must first log in to Twitter.");
				System.out.println("Next, load the following site to grant access to this application: ");
				System.out.println(requestToken.getAuthorizationURL());
				System.out.print("Enter the PIN and press enter to continue: ");
				try
				{
					final String pin = br.readLine();
					if(pin.length() > 0)
					{
						accessToken = tw.getOAuthAccessToken(requestToken, pin);
					}
					else
					{
						accessToken = tw.getOAuthAccessToken();
					}
				}
				catch(final TwitterException ex)
				{
					if(401 == ex.getStatusCode())
					{
						System.out.println("Unable to get the access token.");
					}
					else
					{
						ex.printStackTrace();
					}
				}
				catch(final IOException ex)
				{
					ex.printStackTrace();
				}
			}

			prop.setProperty("oauth.accessToken", accessToken.getToken());
			prop.setProperty("oauth.accessTokenSecret", accessToken.getTokenSecret());
			savePropertiesFile();
		}
		else
		{
			accessToken =
				new AccessToken(prop.getProperty("oauth.accessToken"), prop
					.getProperty("oauth.accessTokenSecret"));
		}

		// get an authorized Twitter instance
		tw.setOAuthAccessToken(accessToken);
	}

	/**
	 * Checks our property list for an existing Consumer Token, and allows the user to either keep the
	 * current token, override an existing token, or enter a new one if none exists.
	 */
	public static void loadConsumerToken()
	{
		final boolean missing =
			prop.getProperty("oauth.consumerKey") == null
				|| prop.getProperty("oauth.consumerSecret") == null;
		boolean reset = false;

		if(!missing)
		{
			System.out.println("An existing Consumer Token was found in " + propfile);
			System.out.print("Do you want to reset the Consumer Token anyway? [y|N] ");
			final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

			try
			{
				final String line = br.readLine().trim();
				reset = line.startsWith("y") || line.startsWith("Y");
			}
			catch(final IOException ex)
			{
				ex.printStackTrace();
			}
		}

		if(missing || reset)
		{
			String consumerKey = null;
			String consumerSecret = null;

			final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

			System.out.print("Please enter the application's Consumer Key: ");

			try
			{
				consumerKey = br.readLine();
			}
			catch(final IOException ex)
			{
				ex.printStackTrace();
			}

			System.out.print("Please enter the application's Consumer Secret: ");

			try
			{
				consumerSecret = br.readLine();
			}
			catch(final IOException ex)
			{
				ex.printStackTrace();
			}

			prop.setProperty("oauth.consumerKey", consumerKey);
			prop.setProperty("oauth.consumerSecret", consumerSecret);
			savePropertiesFile();
		}

		tw.setOAuthConsumer(prop.getProperty("oauth.consumerKey"), prop
			.getProperty("oauth.consumerSecret"));
	}

	/**
	 * Loads a properties file from disk
	 */
	public static void loadPropertiesFile()
	{
		FileInputStream is = null;

		try
		{
			is = new FileInputStream(propfile);
			prop.load(is);
		}
		catch(final FileNotFoundException ex)
		{
			System.out.println("Properties file " + propfile + " not found.");
		}
		catch(final IOException ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			if(is != null)
			{
				try
				{
					is.close();
				}
				catch(final Exception ex)
				{}
			}
		}
	}

	/**
	 * Takes a properties filename from the command line, reads the keys in that file, and then
	 * prompts the user to create new consumer tokens or access tokens if necessary. Saves the most
	 * recent tokens back to the properties file when finished.
	 * 
	 * @param args
	 *          <code>args[0]</code> should contain the name of a property file; otherwise the default
	 *          name "tweater.properties" is used.
	 */
	public static void main(final String[] args)
	{
		propfile = "tweater.properties";

		if(args.length > 0)
			propfile = args[0];

		prop = new Properties();
		tw = new TwitterStreamFactory().getInstance();
		loadPropertiesFile();
		System.out.print(//
			"This program will help you acquire the necessary credentials for using the\n" + //
				"Twitter Streaming API. There are two kinds of tokens needed: a Consumer Token\n" + //
				"and an Access Token. The former may already be present in the properties file,\n" + //
				"but the latter requires that you log in to Twitter and authorize TwEater.\n" + //
				"This script will walk you through the process.\n");
		loadConsumerToken();
		loadAccessToken();
		System.out.println("Properties file " + propfile + " updated.");
	}

	/**
	 * Saves an updated properties file to disk
	 */
	public static void savePropertiesFile()
	{
		FileOutputStream os = null;

		try
		{
			os = new FileOutputStream(propfile);
			prop.store(os, propfile);
			os.close();
		}
		catch(final IOException ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			if(os != null)
			{
				try
				{
					os.close();
				}
				catch(final Exception ex)
				{}
			}
		}
	}

}
