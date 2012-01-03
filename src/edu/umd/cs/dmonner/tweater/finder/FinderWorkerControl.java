package edu.umd.cs.dmonner.tweater.finder;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * This is the interface by which an external process can control a running
 * <code>FinderWorker</code> instance.
 * 
 * @author dmonner
 */
public interface FinderWorkerControl extends Remote
{
	/**
	 * Adds to the queue a list of status IDs that this worker will attempt to find. Good practice
	 * says that the number of status IDs submitted should be the same as the result of the
	 * <code>want</code> method.
	 * 
	 * @param status_ids
	 *          Status IDs to look up
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void enqueue(List<Long> status_ids) throws RemoteException;

	/**
	 * Instructs the worker to cease operation.
	 * 
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public void shutdown() throws RemoteException;

	/**
	 * @return The number of status IDs that this worker wants. This number is equal to the number of
	 *         outstanding Twitter REST API requests remaining minus the current size of the queue.
	 * @throws RemoteException
	 *           If a network error occurs
	 */
	public int want() throws RemoteException;
}
