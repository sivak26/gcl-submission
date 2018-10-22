package org.usagreencardlottery.utils.mail;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;
import java.io.*;
import java.util.Properties;
import java.util.List;

public class Mailer implements Serializable {

    public static int sendMail(String sender, String receiver, String subject, String messageBody, String fileName) {
        try {

            Properties props = new Properties();
            props.put("mail.host", "mail.usagreencardlottery.org");
            props.put("mail.smtp.host", "mail.usagreencardlottery.org");
            props.put("mail.smtp.port", "587");
            Session mailConnection = Session.getInstance(props, null);

            final MimeMessage msg = new MimeMessage(mailConnection);

            Address from = new InternetAddress(sender);

	    // infact the to address is declared as an array
            // to facilitate multiple receipents for a single mail
            Address[] to = InternetAddress.parse(receiver);

            msg.setFrom(from);
            msg.setRecipients(Message.RecipientType.TO, to);
            //msg.setRecipients(Message.RecipientType.BCC, "id-leads@dcis.net");
            msg.setSubject(subject);
            //msg.setContent(messageBody, "text/plain");

            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("As subject - List of users");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            messageBodyPart = new MimeBodyPart();
            DataSource sourceEden = new FileDataSource(fileName);
            messageBodyPart.setDataHandler(new DataHandler(sourceEden));
            messageBodyPart.setFileName(fileName);
            multipart.addBodyPart(messageBodyPart);
            msg.setContent(multipart);
	    // This can take a non-trivial amount of time so
            // spawn a thread to handle it.
            // This will be of advantage when sending bulk mail or in case
            // of mail server connectivity delays.

            Runnable r = new Runnable() {
                public void run() {
                    try {
                        Transport.send(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            Thread t = new Thread(r);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    public static int sendCCMail(String sender, String receiver, String copyMail, String subject, String messageBody, String fileName) {
        try {

            Properties props = new Properties();
            props.put("mail.host", System.getProperty("mail.host").toString());
            props.put("mail.smtp.host", System.getProperty("mail.host").toString());
            props.put("mail.smtp.port", "587");
            Session mailConnection = Session.getInstance(props, null);

            final MimeMessage msg = new MimeMessage(mailConnection);

            Address from = new InternetAddress(sender);

	    // infact the to address is declared as an array
            // to facilitate multiple receipents for a single mail
            Address[] to = InternetAddress.parse(receiver);

            msg.setFrom(from);
            msg.setRecipients(Message.RecipientType.TO, to);
            msg.setRecipients(Message.RecipientType.CC, copyMail);
            //msg.setRecipients(Message.RecipientType.BCC, "id-leads@dcis.net");
            msg.setSubject(subject);
            //msg.setContent(messageBody, "text/plain");

            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("As subject - List of users");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            messageBodyPart = new MimeBodyPart();
            DataSource sourceEden = new FileDataSource(fileName);
            messageBodyPart.setDataHandler(new DataHandler(sourceEden));
            messageBodyPart.setFileName(fileName);
            multipart.addBodyPart(messageBodyPart);
            msg.setContent(multipart);
	    // This can take a non-trivial amount of time so
            // spawn a thread to handle it.
            // This will be of advantage when sending bulk mail or in case
            // of mail server connectivity delays.

            Runnable r = new Runnable() {
                public void run() {
                    try {
                        Transport.send(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            Thread t = new Thread(r);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    //
    //  Send mail as HTML
    //
    public int sendMailHtml(String sender, String receiver, String subject, String messageBody) {
        try {

            Properties props = new Properties();
            props.put("mail.host", "mail.usagreencardlottery.org");
            props.put("mail.smtp.host", "mail.usagreencardlottery.org");
            props.put("mail.smtp.port", "587");
            Session mailConnection = Session.getInstance(props, null);

            final MimeMessage msg = new MimeMessage(mailConnection);

            Address from = new InternetAddress(sender);

	    // infact the to address is declared as an array
            // to facilitate multiple receipents for a single mail
            Address[] to = InternetAddress.parse(receiver);

            msg.setFrom(from);
            msg.setRecipients(Message.RecipientType.TO, to);
            msg.setRecipients(Message.RecipientType.BCC, "cs-usagcl2013@usagreencardlottery.org");
            //msg.setRecipients(Message.RecipientType.BCC, "mageshwaran@dcis.net");
            msg.setSubject(subject);
            msg.setContent(messageBody, "text/html");

	    // This can take a non-trivial amount of time so
            // spawn a thread to handle it.
            // This will be of advantage when sending bulk mail or in case
            // of mail server connectivity delays.
            System.err.println("Attempting to send mail to " + receiver + " with subject as " + subject);

            Runnable r = new Runnable() {
                public void run() {
                    try {
                        Transport.send(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            Thread t = new Thread(r);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    public void mailUser(String senderMail, String userMail, String subject, String messageBody, List mailArguments) {
        //getting contents from Resource Bundle for senderMail,subject and messageBody
        UIMessageResource uiMessage = new UIMessageResource();
        uiMessage.getBundle();
        senderMail = uiMessage.getMessage(senderMail);
        //subject = uiMessage.getMessage(subject);
        messageBody = uiMessage.getMessage(messageBody);
        // Set the mail Arg to 2 since both SenderMail Id will be 0 and userMail Id will be 1.
        // First Check how many arguments have been passed and create the Object Array accordingly.
        int mailArg = 2;
        for (Iterator iter = mailArguments.listIterator(); iter.hasNext();) {
            mailArg++;
            System.err.println("MailArg " + mailArg + ":" + (String) iter.next());
        }
        // Create Object Array.
        Object[] arguments = new Object[mailArg];
        // Set both the senderMail and
        // userMail
        arguments[0] = senderMail;
        arguments[1] = userMail;
        // Now set the passed mail arguments to the Object Array.
        int mailArg1 = 2;
        for (Iterator iter = mailArguments.listIterator(); iter.hasNext();) {
            arguments[mailArg1++] = (String) iter.next();
            System.err.println("MailArg" + mailArg1);
        }
        // Pass the messageBody and arguments
        messageBody = java.text.MessageFormat.format(messageBody, arguments);
        // Send the mail
        sendMailHtml(senderMail, userMail, subject, messageBody);
    }

}
