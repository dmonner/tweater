package edu.umd.cs.dmonner.tweater;

import twitter4j.Status;

/**
 * A wrapper for a <code>Status</code> that makes certain attributes easier to match against
 * <code>QueryItem</code>s. This is done by copying the status text, lowercasing it, and removing
 * newlines.
 * 
 * @author dmonner
 */
public class MatchableStatus
{
	/**
	 * The original status
	 */
	public final Status status;
	/**
	 * The modified status text, suitable for matching.
	 */
	public final String text;

	public MatchableStatus(final Status status)
	{
		this.status = status;
		this.text = status.getText().toLowerCase().replaceAll("\n", " ");
	}
}
