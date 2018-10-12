/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.usagreencardlottery.services;

/**
 *
 * @author shiva kumar
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.MultipartPostMethod;

public class CaptchaHandler {

    public org.apache.log4j.Category LOGGER = org.apache.log4j.Category.getInstance("com.bp.helpers.http.CaptchaHandler");
    public long userId = -1l;
    public String appType;
    public static final int CAPTCHA_POPULATION_FAILED = 309;
    public static final int ERROR_CAPTCHA_SUBMIT_FAILED = 310;
    public Process p = null;

    /**
     * Handles captcha part of the application submission process
     *
     * @param postApplication Object used to submit normal pages
     * @param appType application type (P-primary / S-spouse)
     * @return
     */
    public boolean handleCaptcha(PostApplication postApplication, String appType) {
        boolean hasSuccessfullySubmitted = false;
        this.userId = postApplication.thisApp.getUserId();
        this.appType = appType;
        String url = null;
        String baseURL = null;
        try {
            url = new String(postApplication.method.getURI().getRawURI());
            baseURL = url.substring(0, url.lastIndexOf("/"));

        } catch (URIException e) {
            LOGGER.info("Could not download captcha image ... for user  >>>> " + userId);
            e.printStackTrace();
        }

        String captchaImageURL = null;
        if (postApplication.responseBody != null) {
            String regEx = postApplication.captchaProperties.getProperty("captcha.image.id") + ".*";
            Matcher matcher = Pattern.compile(regEx).matcher(new String(postApplication.responseBody));
            if (matcher.find()) {
                captchaImageURL = matcher.group(0);
                //LOGGER.info("1 :: " +captchaImageURL);
                captchaImageURL = captchaImageURL.replaceAll(".*src=\"", "");
                //LOGGER.info("2 :: " +captchaImageURL);
                captchaImageURL = captchaImageURL.replaceAll("\"\\s+alt=.*", "");
                //LOGGER.info("3 :: " +captchaImageURL);
                captchaImageURL = captchaImageURL.replaceAll("\"\\s+alt=.*", "");
                //LOGGER.info("4 :: " +captchaImageURL);
                // captchaSoundURL = captchaImageURL.replaceAll("image", "sound");
                //LOGGER.info("CAPTCHA URL >>> " + captchaImageURL);
            }
            int location = captchaImageURL.indexOf("&d=");
            if (location != -1) {
                captchaImageURL = captchaImageURL.substring(0, location);
            }
            Calendar cal = Calendar.getInstance();
            long time = cal.getTimeInMillis() + (cal.getTimeZone().getOffset(cal.getTimeInMillis()) * 60000);

            captchaImageURL = captchaImageURL + "&d=" + time;

            if ((captchaImageURL.indexOf("&e=") == -1) && (baseURL.contains("https:"))) {
                captchaImageURL = captchaImageURL + "&e=1";
            }

            hasSuccessfullySubmitted = downloadCaptchaImage(postApplication, baseURL + "/" + captchaImageURL);
            if (hasSuccessfullySubmitted) {
                displayCaptchaImage();
                String captchaText = getCaptchaText();
                p.destroy();
                hasSuccessfullySubmitted = submitCaptcha(postApplication, captchaText, url);
            } else {
                LOGGER.info("Could not download captcha image ...");
            }
        }
        return hasSuccessfullySubmitted;
    }

    public boolean submitCaptcha(PostApplication postApplication, String captchaText, String url) {
        boolean submitted = false;
        MultipartPostMethod multiPart = new MultipartPostMethod(url);
        multiPart.setFollowRedirects(true);
        if (!populateCaptchaPage(multiPart, postApplication, captchaText)) {
            LOGGER.info("Captcha population failed...");
            postApplication.setErrorNo(CAPTCHA_POPULATION_FAILED);
            postApplication.setSubmitStatus(postApplication.APP_SUBMIT_FAILED);
        } else {
            int statusCode = postApplication.postData(multiPart, "CAPTCHA", url);
            if ((statusCode == -1) || (statusCode != 200)) {
                postApplication.setErrorNo(ERROR_CAPTCHA_SUBMIT_FAILED);
                postApplication.setSubmitStatus(postApplication.APP_SUBMIT_FAILED);
                postApplication.savePageAs("error/" + postApplication.fileBaseName + "_captcha_error_resp.html", postApplication.responseBody);
            } else if (postApplication.pageContainsCaptcha()) {
                LOGGER.info("There is an error submiting captha ...");
                submitted = false;
            } else {
                postApplication.setErrorNo(postApplication.ERROR_NOTHING);
                postApplication.setSubmitStatus(postApplication.APP_SUBMIT_SUCCEEDED);
                postApplication.savePageAs("submit/" + postApplication.fileBaseName + "_" + appType + "_confirm.html", postApplication.responseBody);
                submitted = true;
            }
        }

        postApplication.savePageAs("captcha/" + postApplication.fileBaseName + "_" + appType + ".html", postApplication.responseBody);
        return submitted;
    }

    /**
     * populates captcha text and submits
     *
     * @param multiPart
     * @param postApplication
     * @param captchaText
     * @return
     */
    public boolean populateCaptchaPage(MultipartPostMethod multiPart, PostApplication postApplication, String captchaText) {
        boolean captchaPagePopulationStatus = false;
        multiPart.addParameter(postApplication.captchaProperties.getProperty("captcha.text.control.name"), captchaText);
        multiPart.addParameter(postApplication.captchaProperties.getProperty("captcha.submit.button.name"),
                postApplication.captchaProperties.getProperty("captcha.submit.button.value"));

        // populating captcha specific hidden parameters
        String lbdVct = null;
        String lbdVctName = postApplication.captchaProperties.getProperty("captcha.hidden.control.captcha.name");
        String lbdVctValue;
        String regEx = lbdVctName + ".*";
        Matcher matcher = Pattern.compile(regEx).matcher(new String(postApplication.responseBody));

        if (matcher.find()) {

            lbdVct = matcher.group(0);
            lbdVctValue = lbdVct.replaceAll(".*value=\"", "");
            lbdVctValue = lbdVctValue.substring(0, lbdVctValue.indexOf("\""));

            multiPart.addParameter(lbdVctName, lbdVctValue);
            captchaPagePopulationStatus = true;
        }

        LOGGER.info("-------------------------- [ Populating Captcha End ] -------------------------");

        return captchaPagePopulationStatus;
    }

    /**
     * Retrieve the captcha text through console
     *
     * @return
     */
    public String getCaptchaText() {
        String captchaText = null;
        try {
	    System.out.println("If you would like to Quit from Submission to take a Break, Hit Ctrl+C Key - It is Safe to do now and Not While an Application is Being Processed");
	    System.out.println("OR If you are Ready to Submit the Application, go ahead and ");
            System.out.println("Enter Captcha Text : ");
            InputStreamReader isr = new InputStreamReader(System.in, "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            captchaText = br.readLine();
            System.out.println("Enter Captcha Text : " + captchaText + " <<<< ");
            captchaText = captchaText.replaceAll("\\n|\\r", "");
        } catch (IOException e) {
            LOGGER.info("Could not read data from console >>> ", e);
        }
        return captchaText;
    }

    /**
     * display downloaded captcha image.
     */
    public void displayCaptchaImage() {
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    p = Runtime.getRuntime().exec("eog captcha-images/" + userId + "_" + appType + "_captcha.png");

                } catch (IOException e) {
                    // Handle error.
                    e.printStackTrace();
                }
            }
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Downloads captcha image
     *
     * @param postApplication
     * @param captchaImageURL
     * @return
     */
    public boolean downloadCaptchaImage(PostApplication postApplication, String captchaImageURL) {
        boolean downloadStatus = false;
        HttpMethod method = new GetMethod(captchaImageURL);
        method.setFollowRedirects(true);
        // method.setRequestHeader(new Header());
        try {
            int statusCode = postApplication.client.executeMethod(method);
            //method.setFollowRedirects(true);
            //  if ((statusCode == -1) || (statusCode != 200)) {
            InputStream responseBodyStream = method.getResponseBodyAsStream();
            File file = new File("captcha-images/" + postApplication.thisApp.getUserId() + "_" + appType + "_captcha.png");
            if (file.exists()) {
                file.delete();
            }
            //FileOutputStream out = new FileOutputStream(postApplication.thisApp.getUserId() + "_captcha.jpeg");
            FileOutputStream out = new FileOutputStream("captcha-images/" + postApplication.thisApp.getUserId() + "_" + appType + "_captcha.png");
            byte[] buffer = new byte[10000];
            int count = -1;
            while ((count = responseBodyStream.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
            out.close();
            downloadStatus = true;
            // }
        } catch (IOException e) {
            LOGGER.info("There is error downloading the captcha image ... ");
        }
        return downloadStatus;
    }
}
