package org.jython.book.interfaces;

public interface SentimentAnalyzer {
	public Object featurify(String text, Object master);
	public double process(String text, String query);
	public double process(String text);
}
