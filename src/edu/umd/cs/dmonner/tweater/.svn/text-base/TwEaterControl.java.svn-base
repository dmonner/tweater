package edu.umd.cs.dmonner.tweater;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface with which another process can control a running TwEater instance.
 * 
 * @author dmonner
 */
public interface TwEaterControl extends Remote
{
	/**
	 * @return <code>true</code> iff this TwEater instance is currently collecting data from Twitter
	 *         (and not merely working through its backlog)
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public boolean collecting() throws RemoteException;

	/**
	 * @return The fraction of possible memory this TwEater instance is currently using.
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public float memory() throws RemoteException;

	/**
	 * Instructs this TwEater instance to cease collection from Twitter (though it can continue
	 * working through its backlog).
	 * 
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void shutdown() throws RemoteException;

	/**
	 * @return The number of statuses in this TwEater instance's backlog
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public int size() throws RemoteException;
}
