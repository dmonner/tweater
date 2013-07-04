package edu.umd.cs.dmonner.tweater;

/**
 * A <code>QueryItem</code> that matches on the user ID of the originator of the <code>Status</code>
 * .
 * 
 * @author dmonner
 */
public class QueryFollow extends QueryItem
{
	private static final long serialVersionUID = 1L;

	/**
	 * The user ID whose <code>Status</code>es this object will match.
	 */
	public final long userid;

	/**
	 * Creates a new <code>QueryFollow</code> with the given group number, unique ID, and the user ID
	 * of the user we wish to follow.
	 * 
	 * @param group
	 * @param id
	 * @param userid
	 */
	public QueryFollow(final int group, final long id, final int userid)
	{
		super(Type.FOLLOW, group, id);
		this.userid = userid;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#compareTo(tweater.QueryItem)
	 */
	@Override
	public int compareTo(final QueryItem that)
	{
		int result;

		result = super.compareTo(that);
		if(result != 0)
			return result;

		return (int) (this.userid - ((QueryFollow) that).userid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object other)
	{
		boolean result;

		result = super.equals(other);
		if(!result)
			return result;

		return this.userid == ((QueryFollow) other).userid;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return super.hashCode() + (int) userid;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#matches(tweater.MatchableStatus)
	 */
	@Override
	public boolean matches(final MatchableStatus status)
	{
		return status.status.getUser().getId() == userid;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return String.valueOf(userid);
	}
}
