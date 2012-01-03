package edu.umd.cs.dmonner.tweater;

/**
 * A <code>QueryItem</code> that matches exactly on a sequence of keywords given as a single
 * <code>String</code>.
 * 
 * @author dmonner
 */
public class QueryPhrase extends QueryItem
{
	private static final long serialVersionUID = 1L;

	/**
	 * The phrase to match.
	 */
	public final String string;

	/**
	 * Creates a new <code>QueryPhrase</code> with the given group number, unique ID, and the phrase
	 * that we wish to find.
	 * 
	 * @param group
	 * @param id
	 * @param string
	 */
	public QueryPhrase(final int group, final int id, final String string)
	{
		super(Type.PHRASE, group, id);
		this.string = string.trim().toLowerCase();
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

		return this.string.compareTo(((QueryPhrase)that).string);
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

		return this.string.equals(((QueryPhrase)other).string);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return super.hashCode() + string.hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tweater.QueryItem#matches(tweater.MatchableStatus)
	 */
	@Override
	public boolean matches(final MatchableStatus status)
	{
		return status.text.contains(string);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "\"" + string + "\"";
	}
}
