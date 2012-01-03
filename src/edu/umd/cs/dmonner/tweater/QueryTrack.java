package edu.umd.cs.dmonner.tweater;

/**
 * A <code>QueryItem</code> that matches on set of keywords, which need not necessarily be in order
 * or adjacent, specified as a single whitespace-separated <code>String</code>.
 * 
 * @author dmonner
 */
public class QueryTrack extends QueryItem
{
	private static final long serialVersionUID = 1L;

	/**
	 * Individual keywords being tracked, as extracted from the input string.
	 */
	public final String[] words;
	/**
	 * The original tracking string
	 */
	public final String string;

	/**
	 * Creates a new <code>QueryTrack</code> with the given group number, unique ID, and the
	 * whitespace-separated keywords that we wish to find.
	 * 
	 * @param group
	 * @param id
	 * @param string
	 */
	public QueryTrack(final int group, final int id, final String string)
	{
		super(Type.TRACK, group, id);
		this.string = string.trim().toLowerCase();
		this.words = this.string.split("\\s");
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

		return this.string.compareTo(((QueryTrack)that).string);
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

		return this.string.equals(((QueryTrack)other).string);
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
		for(final String word : words)
			if(!status.text.contains(word))
				return false;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return string;
	}
}
