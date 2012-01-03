package edu.umd.cs.dmonner.tweater.util;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class LogDiffKeeper extends Handler
{
	private final StringBuffer buffer;
	private final Formatter format;

	public LogDiffKeeper()
	{
		buffer = new StringBuffer();
		format = new OneLineFormatter();
	}

	public void clear()
	{
		buffer.delete(0, buffer.length());
	}

	@Override
	public void close() throws SecurityException
	{
		clear();
	}

	@Override
	public void flush()
	{
	}

	public synchronized String get()
	{
		final String rv = buffer.toString();
		clear();
		return rv;
	}

	@Override
	public void publish(final LogRecord record)
	{
		buffer.append(format.format(record));
	}
}
