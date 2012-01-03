package edu.umd.cs.dmonner.tweater;

import java.io.Serializable;

/**
 * Represents some property that can be matched in a <code>Status</code> object. Many
 * <code>QueryItem</code>s can belong to the same group, thus associating them with each other when
 * persisted; one might want to do this for related variations on a query, such as "Ma Bell" and
 * "AT&amp;T".
 * 
 * @author dmonner
 */
public abstract class QueryItem implements Comparable<QueryItem>, Serializable
{
	/**
	 * The types of <code>QueryItem</code>s.
	 * 
	 * @author dmonner
	 */
	public enum Type
	{
		/**
		 * Matches on set of keywords, which need not necessarily be in order or adjacent
		 */
		TRACK,
		/**
		 * Matches exactly on a sequence of keywords
		 */
		PHRASE,
		/**
		 * Matches on the user ID of the originator of the <code>Status</code>
		 */
		FOLLOW
	}

	private static final long serialVersionUID = 1L;;

	/**
	 * The type of this <code>QueryItem</code>
	 */
	public final Type type;
	/**
	 * The number of the group to which this <code>QueryItem</code> belongs
	 */
	public final int group;
	/**
	 * A unique ID number for this <code>QueryItem</code>
	 */
	public final int id;

	/**
	 * Creates a <code>QueryItem</code> with the specified type, group number, and unique ID.
	 * 
	 * @param type
	 * @param group
	 * @param id
	 */
	public QueryItem(final Type type, final int group, final int id)
	{
		this.type = type;
		this.group = group;
		this.id = id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(final QueryItem that)
	{
		int result;

		result = this.type.compareTo(that.type);
		if(result != 0)
			return result;

		result = this.group - that.group;
		if(result != 0)
			return result;

		result = this.id - that.id;
		if(result != 0)
			return result;

		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object other)
	{
		if(other instanceof QueryItem)
		{
			final QueryItem that = (QueryItem)other;
			return this.type == that.type && this.group == that.group && this.id == that.id;
		}

		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return type.hashCode() + 13 * group + 7 * id;
	}

	/**
	 * Determines whether the given status matches this <code>QueryItem</code>.
	 * 
	 * @param status
	 * @return <code>true</code> iff the given status matches this <code>QueryItem</code>
	 */
	public abstract boolean matches(MatchableStatus status);
}
