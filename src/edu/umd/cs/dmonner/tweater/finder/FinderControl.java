package edu.umd.cs.dmonner.tweater.finder;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import twitter4j.Status;

/**
 * This is the interface by which an external process can control a running <code>Finder</code>
 * server instance.
 * 
 * @author dmonner
 */
public interface FinderControl extends Remote
{
	/**
	 * Adds a list of status IDs for the server to find and persist.
	 * 
	 * @param Status
	 *          IDs to find
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void add(List<Long> ids) throws RemoteException;

	/**
	 * Returns the entire list of status IDs that the server has yet to find.
	 * 
	 * @return The list of remaining status IDs
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public List<Long> dump() throws RemoteException;

	/**
	 * Instructs the server to not expect a call to <code>process</code> from the worker tasked with
	 * finding the status with the given ID, because the status was unavailable, invalid, or an error
	 * occurred with the worker.
	 * 
	 * @param status_id
	 *          The ID of the status the server should ignore.
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void ignore(long status_id) throws RemoteException;

	/**
	 * Called by a worker upon finding a status that the server wants. Instructs the server to process
	 * and persist the status using its <code>StatusEater</code> instance.
	 * 
	 * @param status
	 *          The status to process and persist
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void process(Status status) throws RemoteException;

	/**
	 * @return The number of status IDs currently being processed by all available workers.
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public int sent() throws RemoteException;

	/**
	 * @return The number of status IDs currently waiting to be sent to workers.
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public int size() throws RemoteException;

	/**
	 * Instructs the server to cease sending IDs to workers to be processed. Similarly, instructs
	 * workers to stop finding statuses and shut down.
	 * 
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void shutdown() throws RemoteException;
}
