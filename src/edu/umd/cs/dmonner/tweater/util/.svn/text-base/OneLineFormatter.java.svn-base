package edu.umd.cs.dmonner.tweater.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A simple formatter for log records that specifies date, time, level, and message on a single line
 * (with the exception of multi-line messages).
 * 
 * @author dmonner
 */
public class OneLineFormatter extends Formatter
{
	/**
	 * Specifies the format in which dates and times will be displayed
	 */
	public static final DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
	 */
	@Override
	public String format(final LogRecord record)
	{
		return "[" + format.format(new Date(record.getMillis())) + " " + record.getLevel() + "] "
			+ record.getMessage() + "\n";
	}
}
