package edu.umd.cs.dmonner.tweater.util;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * A class that reads email information from a Properties object and can thereafter send email
 * message alerts.
 *
 * @author dmonner
 */
public class AlertEmailer
{
	/**
	 * Sends an email using the given parameters.
	 *
	 * @param smtp
	 *          The SMTP server to send from
	 * @param from
	 *          The from-address
	 * @param to
	 *          The to-address
	 * @param subj
	 *          The subject line
	 * @param body
	 *          The message body
	 * @throws MessagingException
	 *           If the email fails to send
	 */
	public static void send(final String smtp, final String from, final String to, final String subj,
		final String body) throws MessagingException
	{
		// Silently quit if we are missing any required email information
		if(smtp == null || from == null || to == null ||
		   smtp.isEmpty() || from.isEmpty() || to.isEmpty())
			return;

		// Set the host smtp address
		final Properties props = new Properties();
		props.put("mail.smtp.host", smtp);

		// create some properties and get the default Session
		final Session session = Session.getDefaultInstance(props, null);

		// create a message
		final Message msg = new MimeMessage(session);

		// set the from and to address
		final InternetAddress addressFrom = new InternetAddress(from);
		msg.setFrom(addressFrom);
		final InternetAddress addressTo = new InternetAddress(to);
		msg.setRecipient(Message.RecipientType.TO, addressTo);

		// set the subject and body
		msg.setSubject(subj);
		msg.setContent(body, "text/plain");

		// send the email message
		Transport.send(msg);
	}

	/**
	 * The hostname of the SMTP server to use to send messages.
	 */
	private final String smtp;
	/**
	 * The email address to which alert message emails should be sent.
	 */
	private final String toEmail;
	/**
	 * The email address from which alert message emails will originate.
	 */
	private final String fromEmail;

	/**
	 * Reads SMTP server, to-email, and from-email from the given Properties file.
	 *
	 * @param props
	 */
	public AlertEmailer(final Properties props)
	{
		this.smtp = props.getProperty("tweater.mail.smtp");
		this.toEmail = props.getProperty("tweater.mail.to");
		this.fromEmail = props.getProperty("tweater.mail.from");
	}

	/**
	 * Sends an alert message, with the specified subject and message contents, using the SMTP server,
	 * to address, and from address that were read when this object was created.
	 *
	 * @param subj
	 *          The subject line
	 * @param msg
	 *          The message body
	 * @throws MessagingException
	 *           If the email fails to send
	 */
	public void send(final String subj, final String msg) throws MessagingException
	{
		send(smtp, fromEmail, toEmail, subj, msg);
	}
}
