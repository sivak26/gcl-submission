/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author mageshwaran
 */
package org.usagreencardlottery.utils.mail;

//Imports
import java.io.*;
import java.util.ResourceBundle;
import java.util.Locale;

public class UIMessageResource implements Serializable {

    // Create the ResouceBundle which will be used for finding the Messages
    private ResourceBundle resourceBundle = null;
    private String baseName = "org.usagreencardlottery.resources.ResourceBundle";
    // String Variable "value" to store the message and return
    private String messageValue = null;

    public UIMessageResource() {
    }

    /*
     *   getResourceMessageName() A function to find the message of the key
     *  Param: key
     *  Returm: Message from the Resource bundle of the key
     */
    public void getBundle(Locale locale)
            throws java.util.MissingResourceException {

        if (locale == null) {
            this.resourceBundle = ResourceBundle.getBundle(this.baseName, Locale.getDefault());
        } else {
            this.resourceBundle = ResourceBundle.getBundle(this.baseName, locale);
        }
    }

    public void getBundle()
            throws java.util.MissingResourceException {
        this.resourceBundle = ResourceBundle.getBundle(this.baseName, Locale.getDefault());
    }

    public String getMessage(String key) {
        try {

            messageValue = resourceBundle.getString(key);
      //      System.err.println("Message Value Key:" +key);
            //      System.err.println("\n Message Value :" +messageValue);
        } catch (java.util.MissingResourceException e) {
            e.printStackTrace();
        }
        return messageValue;

    }
}
