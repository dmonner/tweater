package edu.umd.cs.dmonner.tweater;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import twitter4j.Status;

/**
 * Classes implementing this interface are responsible for receiving <code>Status</code> objects via
 * <code>process</code>, performing any desired preprocessing on them, and transferring them to
 * long-term storage. Optionally, tweets and location information should be removed (per user
 * request) if the <code>delete</code> or <code>scrubGeo</code> methods are called.
 * 
 * @author dmonner
 */
public interface StatusEater
{
	/**
	 * Adds a <code>QueryItem</code> to the list of items that this <code>StatusEater</code> cares
	 * about.
	 * 
	 * @param item
	 */
	public void addItem(QueryItem item);

	/**
	 * Adds several <code>QueryItem</code>s to the list of items that this <code>StatusEater</code>
	 * cares about.
	 * 
	 * @param item
	 */
	public void addItems(Collection<? extends QueryItem> items);

	/**
	 * Empties this <code>StatusEater</code>'s list of <code>QueryItems</code>.
	 */
	public void clearItems();

	/**
	 * Remotes a specific <code>QueryItem</code> from the list of items that this
	 * <code>StatusEater</code> cares about.
	 * 
	 * @param item
	 */
	public void delItem(QueryItem item);

	public Set<QueryItem> getQuery();

	/**
	 * Instructs this <code>StatusEater</code> to persist the given <code>Status</code>, possibly
	 * including which <code>QueryItem</code>s were matched. The method of persistent storage (for
	 * example, a database or a text file) is chosen by the implementing class.
	 * 
	 * @param matches
	 * @param status
	 */
	public void persist(List<QueryItem> matches, Status status);

	/**
	 * Instructs this <code>StatusEater</code> to match the given <code>Status</code> against its list
	 * of <code>QueryItem</code>s to see which match, and then pass the results along to
	 * <code>persist</code>.
	 * 
	 * @param status
	 */
	public void process(Status status);
}
