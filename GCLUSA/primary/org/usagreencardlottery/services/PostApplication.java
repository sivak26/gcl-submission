package org.usagreencardlottery.services;

import org.usagreencardlottery.model.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpRecoverableException;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.MultipartPostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.io.IOUtils;
import org.usagreencardlottery.model.Applicant;
import org.usagreencardlottery.utils.db.DBConnector;
import org.usagreencardlottery.utils.mail.*;

/**
 * Class to accept the application details bean and submit DV Lottery
 * application(s)
 *
 * @author sandeep
 *
 */
public class PostApplication {

    public AppDetails thisApp = null;
    public org.apache.log4j.Category PostLog = org.apache.log4j.Category.getInstance("com.bp.helpers.http.PostApplication");
    public String viewState = "";
    public String eventValidation = "";
    public String viewStateEncrypted = "";
    public HttpClient client = null;
    public DBConnector connector = null;
    public Properties captchaProperties = new Properties();
    public boolean containsCaptcha = true;

    /**
     * Create a new instance to Application poster class
     *
     * @param thisApp The bean which holds all the details necessary to submit
     * DV application(s)
     */
    public PostApplication(AppDetails thisApp) {
        this.thisApp = thisApp;
        this.connector = new DBConnector();
        client = new HttpClient();
        connector.loadConfig("gclocal.properties");
        try {
            captchaProperties.load(new FileInputStream(new File("captcha.properties")));
        } catch (IOException e) {
            PostLog.info("The application contains no captcha page ...");
            containsCaptcha = false;
        }
    }

    public static final int APP_SUBMIT_STARTED = 200;
    public static final int APP_SUBMIT_SUCCEEDED = 201;
    public static final int APP_SUBMIT_FAILED = 202;
    public static final int ERROR_NOTHING = 301;
    public static final int ERROR_ONE_PROCESS_FAILED = 302;
    public static final int ERROR_PAGE1_FAILED = 303;
    public static final int ERROR_PAGE1_SUBMIT_FAILED = 304;
    public static final int ERROR_PAGE1_DATA_ERROR = 305;
    public static final int ERROR_PAGE2_SUBMIT_FAILED = 306;
    public static final int PART_ONE_POPULATION_FAILED = 307;
    public static final int PART_TWO_POPULATION_FAILED = 307;
    public static final int LOG_ERROR = 1;
    public static final int LOG_INFO = 2;
    private int submitStatus = APP_SUBMIT_STARTED;
    private int errorNo = ERROR_NOTHING;
    public HttpMethod method = null;
    public MultipartPostMethod multiPart = null;
    public byte[] responseBody = null;
    public String pageRetrieved = "";
    public String responsePage = "";
    public String fileBaseName = "";
    public String photoBaseName = "";
    private String currentPageName = "";
    private long pageStartTime = 0;
    private long pageEndTime = 0;
    private long pageTimeTaken = 0;
    private static String urlPage1 = "https://www.dvlottery.state.gov";
    private static String urlPage2 = "https://www.dvlottery.state.gov/application.aspx";
    private String captchaText = null;

    public int getSubmitStatus() {
        return submitStatus;
    }

    public void setSubmitStatus(int submitStatus) {
        this.submitStatus = submitStatus;
    }

    public int getErrorNo() {
        return errorNo;
    }

    public void setErrorNo(int errorNo) {
        this.errorNo = errorNo;
    }

    /**
     * Attempt submitting a single application. If the parameter appType is "P"
     * attempts a submission for primary applicant. If "S" then attempts a
     * submission for spouse by swapping the applicant information with spouse
     *
     * @param appType "P" submit for primary - "S" - submit for spouse
     * @return true if submitted, false otherwise
     */
    public boolean submitApplication(String appType) {
        int statusCode = 0;
        boolean submitted = false;

        //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
        boolean p2req = (thisApp.getApplicant().getMaritalStatus().equals("6") || thisApp.getChildCount() > 0);
        //END NEW CHANGES BASED ON THE MARITAL STATUS

        PostLog.info("========================== [" + thisApp.getUserId() + " - Start ] =============================");
        if (p2req) {
            PostLog.info("FORM PART-2 NEEDS TO BE FILLED AND SUBMITTED");
        }
        if (thisApp.isCouplePlan()) {
            PostLog.info("Couple Plan - Need to submit for Spouse too");
        }

        fileBaseName = Integer.toString(thisApp.getUserId());

        if (appType.equals("S")) {
            fileBaseName = fileBaseName + "-Spouse";
        }
        PostLog.info("File Base Name = " + fileBaseName);

        statusCode = retrievePage(urlPage2);
        if ((statusCode == -1) || (statusCode != 200)) {
            PostLog.error("Failed to recover from exception retrieving " + urlPage1);
            setErrorNo(ERROR_ONE_PROCESS_FAILED);
            setSubmitStatus(APP_SUBMIT_FAILED);
            savePageAs("error/" + fileBaseName + "home.html", responseBody);
        } else {
            multiPart = new MultipartPostMethod(urlPage2);
            multiPart.setFollowRedirects(true);
            if (handleCaptcha(appType)) {
                //PostLog.info("Response Page >>>>" +responsePage);
                savePageAs("page1start/" + fileBaseName + ".html", responseBody);
                URI uri1 = getURI(multiPart);
                multiPart = new MultipartPostMethod();
                try {
                    multiPart.setURI(uri1);
                } catch (URIException e) {
                    e.printStackTrace();
                }
                multiPart.setFollowRedirects(true);
                if (populatePartOne(multiPart, appType, thisApp)) {
                    PostLog.info("Part One populated successfully...");
                    statusCode = postData(multiPart, " ", urlPage2);
                    Header[] locationHeaders = multiPart.getResponseHeaders();
                    /*for(Header h : locationHeaders) {
                     PostLog.info(h.getName() + " >>> " + h.getValue());
                     }*/
                    if ((statusCode == -1) || (statusCode != 200)) {
                        setErrorNo(ERROR_PAGE1_SUBMIT_FAILED);
                        setSubmitStatus(APP_SUBMIT_FAILED);
                        savePageAs("error/" + fileBaseName + "part1errorresp.html", responseBody);
                    } else if (responsePage.indexOf("_ctl0:ContentPlaceHolder1:btnContinueP1") > 0
                            && responsePage.indexOf("At least one data validation error occurred") > 0) {
                        PostLog.info("Error detected in submitting page.. So cancelling application...");
                        savePageAs("error/" + fileBaseName + "part1errorresp.html", responseBody);
                        statusCode = cancelApp(urlPage2, "Cancelling Page 1 submit...");
                    } else if (!p2req) {
                        PostLog.info("Part 2 not required.. So proceeding with submit....");
                        savePageAs("review/" + fileBaseName + ".html", responseBody);
                        //statusCode = submitApp(urlPage2, "Single app submit....", appType);
                        statusCode = submitReviewPage(urlPage2, "Single app submit....");
                        if ((statusCode == -1) || (statusCode != 200)) {
                            setErrorNo(APP_SUBMIT_FAILED);
                            setSubmitStatus(APP_SUBMIT_FAILED);
                            savePageAs("error/" + fileBaseName + "submitApp.html", responseBody);
                        } else {
                            //submitted = handleCaptcha(appType);
                            submitted = true;
                            if (submitted) {
                                confirmSubmission(appType);
                            } else {
                                PostLog.info("ERROR In Captcha Page ....");
                            }
                            setErrorNo(ERROR_NOTHING);
                            setSubmitStatus(APP_SUBMIT_SUCCEEDED);
                            savePageAs("submit/" + fileBaseName + "confirm.html", responseBody);
                            //submitted = true;
                        }
                    } else {
                        //PostLog.info("Response Page >>>>" +responsePage);
                        savePageAs("page2start/" + fileBaseName + ".html", responseBody);
                        //multiPart = new MultipartPostMethod(urlPage2);
                        URI uri = getURI(multiPart);
                        multiPart = new MultipartPostMethod();
                        try {
                            multiPart.setURI(uri);
                        } catch (URIException e) {
                            e.printStackTrace();
                        }
                        // String url = getURL(multiPart);
                        // multiPart = new MultipartPostMethod(url);
                        multiPart.setFollowRedirects(true);
                        if (!populatePartTwo(multiPart, appType, thisApp)) {
                            PostLog.info("Part two population failed...");
                            setErrorNo(PART_ONE_POPULATION_FAILED);
                            setSubmitStatus(APP_SUBMIT_FAILED);
                        } else {
                            //statusCode = postData(multiPart, " ", urlPage2);
                            statusCode = postData(multiPart, " ", urlPage2);
                            locationHeaders = multiPart.getResponseHeaders();

                            /*for(Header h : locationHeaders) {
                             PostLog.info(h.getName() + " >>> " + h.getValue());
                             }*/
                            //PostLog.info("AFTER PART TWO SUBMISSION :: " +statusCode);
                            if ((statusCode == -1) || (statusCode != 200)) {
                                if (statusCode == 302) {
                                    Header[] locationHeaders1 = multiPart.getResponseHeaders();
                                    /*for(Header h : locationHeaders1) {
                                     PostLog.info(h.getName() + " >>> " + h.getValue());
                                     }*/
                                    String redirectLocation;
                                    Header locationHeader = method.getResponseHeader("location");
                                    //PostLog.info("HEADER >>>" + locationHeader);
                                    //PostLog.info("REDITECT >>> " + locationHeader.getName() + locationHeader.getValue());
                                    if (locationHeader != null) {
                                        redirectLocation = locationHeader.getValue();
                                        //PostLog.info("REDIRECTION URL >>> " + redirectLocation);
                                        try {
                                            multiPart.setURI(new URI(redirectLocation));
                                        } catch (URIException e) {
                                            e.printStackTrace();
                                        }
                                        statusCode = postData(multiPart, " ", redirectLocation);
                                        savePageAs("review/" + fileBaseName + ".html", responseBody);
                                    }
                                }

                                setErrorNo(ERROR_PAGE2_SUBMIT_FAILED);
                                setSubmitStatus(APP_SUBMIT_FAILED);
                                savePageAs("error/" + fileBaseName + "part2errorresp.html", responseBody);
				//} else if (responsePage.indexOf("At least one data validation error occurred") > 0) {
			    } else if (responsePage.indexOf("_ctl0:ContentPlaceHolder1:btnContinueP1") > 0
				       && responsePage.indexOf("At least one data validation error occurred") > 0) {
                                setErrorNo(APP_SUBMIT_FAILED);
                                setSubmitStatus(APP_SUBMIT_FAILED);
                                savePageAs("error/" + fileBaseName + "part2errorresp.html", responseBody);
                                statusCode = cancelApp(urlPage2, "Cancelling Page 2 submit...");
                            } else {
                                savePageAs("review/" + fileBaseName + ".html", responseBody);
                                //statusCode = submitApp(urlPage2, "Single app submit....", appType);
                                statusCode = submitReviewPage(urlPage2, "Single app submit....");
                                if ((statusCode == -1) || (statusCode != 200)) {
                                    setErrorNo(APP_SUBMIT_FAILED);
                                    setSubmitStatus(APP_SUBMIT_FAILED);
                                    savePageAs("error/" + fileBaseName + "submitApp.html", responseBody);
                                } else {
                                    //submitted = handleCaptcha(appType);
                                    submitted = true;
                                    if (submitted) {
                                        confirmSubmission(appType);
                                    } else {
                                        PostLog.info("ERROR In Captcha Page ....");
                                    }
                                    setErrorNo(ERROR_NOTHING);
                                    setSubmitStatus(APP_SUBMIT_SUCCEEDED);
                                    savePageAs("submit/" + fileBaseName + "confirm.html", responseBody);
                                    //submitted = true;
                                }
                            }
                        }
                    }
                } else {
                    setErrorNo(PART_ONE_POPULATION_FAILED);
                    setSubmitStatus(APP_SUBMIT_FAILED);
                    PostLog.info("Population of part one data failed...");
                }
                PostLog.info("========================== [" + thisApp.getUserId() + " - End ] =============================");
            } else {
                PostLog.info("============================= CAPTCHA FAILED =============================");
            }
        }
        return submitted;
    }

    public boolean populatePartOne(MultipartPostMethod multiPart, String appType, AppDetails thisApp) {
        boolean populated = false;
        Applicant primary = null;
        AppDetails userMailingInfo = null;

        PostLog.info("-------------------------- [ Populating part one start ] -------------------------");

        if (appType.equals("P")) {
            primary = thisApp.getApplicant();
            PostLog.info("Populating part One for Applicant");
        } else {
            primary = thisApp.getSpouse();
            PostLog.info("Popluating part One for Spouse");
        }

        // 1.a
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:txtLastName", primary.getLastName());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:cbxLastName", primary.getNoLastName());
        PostLog.info("Last Name = " + primary.getLastName() + "/" + primary.getNoLastName());

        //1.b
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:txtFirstName", primary.getFirstName());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:cbxFirstName", primary.getNoFirstName());
        PostLog.info("First Name = " + primary.getFirstName() + "/" + primary.getNoFirstName());

        //1.c
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:txtMiddleName", primary.getMiddleName());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qName:cbxMiddleName", primary.getNoMiddleName());
        PostLog.info("Middle Name = " + primary.getMiddleName() + "/" + primary.getNoMiddleName());

        // 2 Gender
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qGender:grpGender", primary.getGender());
        PostLog.info("Gender = " + primary.getGender());

        //3.a,b,c
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthDate:txtMonthOfBirth", primary.getBirthMonth());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthDate:txtDayOfBirth", primary.getBirthDay());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthDate:txtYearOfBirth", primary.getBirthYear());
        PostLog.info("Day/Month/Year = " + primary.getBirthDay() + "/" + primary.getBirthMonth() + "/" + primary.getBirthYear());

        // 4 Birth City
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCity:txtBirthCity", primary.getBirthCity());
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCity:cbxBirthCity", primary.getNoBirthCity());
        PostLog.info("Birth City = " + primary.getBirthCity() + "/" + primary.getNoBirthCity());

        // 5 Country where you were born
        if (appType.equals("P")) {
            if (thisApp.getNativeMode().equals("1")) {
                multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCountry:drpBirthCountry", thisApp.getNativeCountry());
                PostLog.info("Birth Country = " + thisApp.getNativeCountry());
            } else {
                multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCountry:drpBirthCountry", primary.getBirthCountry());
                PostLog.info("Birth Country = " + primary.getBirthCountry());
            }
        } else {
            if (thisApp.getNativeMode().equals("2")) {
                multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCountry:drpBirthCountry", thisApp.getNativeCountry());
                PostLog.info("Birth Country = " + thisApp.getNativeCountry());
            } else {
                multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qBirthCountry:drpBirthCountry", primary.getBirthCountry());
                PostLog.info("Birth Country = " + primary.getBirthCountry());
            }
        }

        // 6 Applying based on Country of eligibility? - Assuming ineligible
        if (primary.getBirthCountry().equals(primary.getEligibleCountry())) {
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEligibilityCountry:rblBirthEligibleCountry", "Y");
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEligibilityCountry:drpBirthEligibleCountry", "0");
            PostLog.info("Applying based on birth country = Y/0");
        } else {
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEligibilityCountry:rblBirthEligibleCountry", "N");
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEligibilityCountry:drpBirthEligibleCountry", primary.getEligibleCountry());
            PostLog.info("Applying based on birth country = N/" + primary.getEligibleCountry());
        }

        // 7 have the photo retrieved from DB for the applicant and provide the file name
        File photoFile = null;
        FilePart photoPart = null;

        try {
            PostLog.info("Photo file Name = " + primary.getPhotoFileName());
            photoFile = new File(primary.getPhotoFileName());
            photoPart = new FilePart("_ctl0:ContentPlaceHolder1:formApplicant:qPhotograph:inpPhotograph", photoFile, "image/jpeg", null);
            multiPart.addParameter(photoFile.getName(), photoFile.getName(), photoFile);
            multiPart.addPart(photoPart);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList contacts = thisApp.getContact();
        for (int i = 0; i < contacts.size(); i++) {
            userMailingInfo = (AppDetails) contacts.get(i);
            // 8 Mailing address
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtInCareOf", userMailingInfo.getFirstNameCareOf());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtAddress1", userMailingInfo.getApartmentNumber());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtAddress2", userMailingInfo.getStreet());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtCity", userMailingInfo.getCity());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtDistrict", userMailingInfo.getState());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:txtZipCode", userMailingInfo.getZipcode());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:cbxZipCode", userMailingInfo.getNoZipcode());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMailingAddress:drpMailingCountry", userMailingInfo.getCountry());
            // 11 Email id
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEmailAddress:txtEmailAddress", userMailingInfo.getEmailIdToContact());
            // Confirm email address
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEmailAddress:txtConfEmailAddress", userMailingInfo.getEmailIdToContact());

            PostLog.info(">>>> Contact Info Begins <<<<");
            PostLog.info("Care Of :: " + userMailingInfo.getFirstNameCareOf() + " " + userMailingInfo.getLastNameCareOf());
            PostLog.info("Address Line 1 :: " + userMailingInfo.getApartmentNumber());
            PostLog.info("Address Line 2 :: " + userMailingInfo.getStreet());
            PostLog.info("City :: " + userMailingInfo.getCity());
            PostLog.info("State :: " + userMailingInfo.getState());
            PostLog.info("Zip Code :: " + userMailingInfo.getZipcode());
            PostLog.info("Country :: " + userMailingInfo.getCountry());
            PostLog.info("Email Id :: " + userMailingInfo.getEmailIdToContact());
            PostLog.info(">>>> Contact Info Ends <<<<");
        }

        // 9 country where you live
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qResidence:drpCountry", primary.getLivingCountry());
        PostLog.info("Living Country = " + primary.getLivingCountry());

        // 10 Phone number
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qPhoneNumber:txtPhoneNumber", "");

        // 12 Education
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qEducation:rblEducation", primary.getEducation());
        PostLog.info("Education = " + primary.getEducation());

        // 13 Marital status
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qMarried:rblMarried", primary.getMaritalStatus());
        PostLog.info("Marital Status = " + primary.getMaritalStatus());

        // 14 Child count
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:formApplicant:qNumChildren:txtNumChildren", primary.getChildCount());
        PostLog.info("Child Count = " + primary.getChildCount());

        // 15 Submit button
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:btnContinueP1", "Continue");
        populated = true;

        PostLog.info("-------------------------- [ Populating part one end ] -------------------------");
        return populated;
    }

    public boolean populatePartTwo(MultipartPostMethod multiPart, String appType, AppDetails thisApp) {
        Applicant spouse = null;
        Child thisChild = null;
        File photoFile = null;
        FilePart photoPart = null;
        int i = 1;

        PostLog.info("-------------------------- [ Populating part two start ] -------------------------");

        //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
        if (thisApp.getApplicant().getMaritalStatus().equals("6")) {
            //END NEW CHANGES BASED ON THE MARITAL STATUS

            PostLog.info(".................... [ Populating Spouse Details start ] ......................");

            if (appType.equals("P")) {
                spouse = thisApp.getSpouse();
            } else {
                spouse = thisApp.getApplicant();
            }

            // 13.a
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:txtLastName", spouse.getLastName());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:cbxLastName", spouse.getNoLastName());
            PostLog.info("Last Name =" + spouse.getLastName() + "/" + spouse.getNoLastName());

            //13.b
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:txtFirstName", spouse.getFirstName());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:cbxFirstName", spouse.getNoFirstName());
            PostLog.info("First Name = " + spouse.getFirstName() + "/" + spouse.getNoFirstName());

            //13.c
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:txtMiddleName", spouse.getMiddleName());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qName:cbxMiddleName", spouse.getNoMiddleName());
            PostLog.info("Middle Name = " + spouse.getMiddleName() + "/" + spouse.getNoMiddleName());

            //13.d
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthDate:txtMonthOfBirth", spouse.getBirthMonth());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthDate:txtDayOfBirth", spouse.getBirthDay());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthDate:txtYearOfBirth", spouse.getBirthYear());
            PostLog.info("Day/Month/Year = " + spouse.getBirthDay() + "/" + spouse.getBirthMonth() + "/" + spouse.getBirthYear());

            //13.e
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qGender:grpGender", spouse.getGender());
            PostLog.info("Gender = " + spouse.getGender());

            // 13.f Birth City
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCity:txtBirthCity", spouse.getBirthCity());
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCity:cbxBirthCity", spouse.getNoBirthCity());
            PostLog.info("Brith city = " + spouse.getBirthCity() + "/" + spouse.getNoBirthCity());

            // 13.g Country of Birth
            if (appType.equals("P")) {
                if (thisApp.getNativeMode() == "2") {
                    multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCountry:drpBirthCountry", thisApp.getNativeCountry());
                    PostLog.info("Birth country = " + thisApp.getNativeCountry());
                } else {
                    multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCountry:drpBirthCountry", spouse.getBirthCountry());
                    PostLog.info("Birth Country = " + spouse.getBirthCountry());
                }
            } else {
                if (thisApp.getNativeMode().equals("1")) {
                    multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCountry:drpBirthCountry", thisApp.getNativeCountry());
                    PostLog.info("Brith Country = " + thisApp.getNativeCountry());
                } else {
                    multiPart.addParameter("_ctl0:ContentPlaceHolder1:formSpouse:qBirthCountry:drpBirthCountry", spouse.getBirthCountry());
                    PostLog.info("Birth Country = " + spouse.getBirthCountry());
                }
            }

            // Spouse Photo
            try {
                PostLog.info("Photo File = " + spouse.getPhotoFileName());
                photoFile = new File(spouse.getPhotoFileName());
                photoPart = new FilePart("_ctl0:ContentPlaceHolder1:formSpouse:qPhotograph:inpPhotograph", photoFile, "image/jpeg", null);
                multiPart.addParameter(photoFile.getName(), photoFile.getName(), photoFile);
                multiPart.addPart(photoPart);
            } catch (Exception e) {
                e.printStackTrace();
            }
            PostLog.info(".................... [ Populating Spouse Details end ] ......................");

        }

        if (thisApp.getChildCount() > 0) {

            PostLog.info("................. [Child count = " + thisApp.getChildCount() + " ]...............");

            ArrayList children = thisApp.getChildren();

            for (i = 1; i <= children.size(); i++) {
                thisChild = (Child) children.get(i - 1);
                PostLog.info(". . . . . . . . . . . [Child " + i + " start ] . . . . . . . . . . ");
                String suffix = Integer.toString(i);
                if (i < 10) {
                    suffix = "0" + suffix;
                }
                String formWord = "_ctl0:ContentPlaceHolder1:formChild" + suffix + ":";

                // 14.a
                multiPart.addParameter(formWord + "qName:txtLastName", thisChild.getLastName());
                multiPart.addParameter(formWord + "qName:cbxLastName", thisChild.getNoLastName());
                PostLog.info("Last Name = " + thisChild.getLastName() + "/" + thisChild.getNoLastName());

                //14.b
                multiPart.addParameter(formWord + "qName:txtFirstName", thisChild.getFirstName());
                multiPart.addParameter(formWord + "qName:cbxFirstName", thisChild.getNoFirstName());
                PostLog.info("First Name = " + thisChild.getFirstName() + "/" + thisChild.getNoFirstName());

                //14.c
                multiPart.addParameter(formWord + "qName:txtMiddleName", thisChild.getMiddleName());
                multiPart.addParameter(formWord + "qName:cbxMiddleName", thisChild.getNoMiddleName());
                PostLog.info("Middle Name =" + thisChild.getMiddleName() + "/" + thisChild.getNoMiddleName());

                //14.d
                multiPart.addParameter(formWord + "qBirthDate:txtMonthOfBirth", thisChild.getBirthMonth());
                multiPart.addParameter(formWord + "qBirthDate:txtDayOfBirth", thisChild.getBirthDay());
                multiPart.addParameter(formWord + "qBirthDate:txtYearOfBirth", thisChild.getBirthYear());
                PostLog.info("Day/Month/Year = " + thisChild.getBirthDay() + "/" + thisChild.getBirthMonth() + "/" + thisChild.getBirthYear());

                //14.e
                multiPart.addParameter(formWord + "qGender:grpGender", thisChild.getGender());
                PostLog.info("Gender = " + thisChild.getGender());

                // 14.f Birth City
                multiPart.addParameter(formWord + "qBirthCity:txtBirthCity", thisChild.getBirthCity());
                multiPart.addParameter(formWord + "qBirthCity:cbxBirthCity", thisChild.getNoBirthCity());
                PostLog.info("BirthCity = " + thisChild.getBirthCity() + "/" + thisChild.getNoBirthCity());

                // 14.g Country of Birth
                multiPart.addParameter(formWord + "qBirthCountry:drpBirthCountry", thisChild.getBirthCountry());
                PostLog.info("Birth Country = " + thisChild.getBirthCountry());

                // Child Photo
                photoFile = null;
                photoPart = null;

                try {
                    PostLog.info("Photo of child " + i + " = " + thisChild.getPhotoFileName());
                    photoFile = new File(thisChild.getPhotoFileName());
                    photoPart = new FilePart(formWord + "qPhotograph:inpPhotograph", photoFile, "image/jpeg", null);
                    multiPart.addParameter(photoFile.getName(), photoFile.getName(), photoFile);
                    multiPart.addPart(photoPart);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                PostLog.info(". . . . . . . . . . . [Child " + i + " end ] . . . . . . . . . . ");
            }
        }

        PostLog.info("................. [Child population end ]...............");

        //15 Submit button
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:btnReview", "Continue");
        PostLog.info("SUBMITTED PART TWO");
        return true;
    }

    public void setViewState(String pageRetrieved) {
        if (pageRetrieved.indexOf("__VIEWSTATE") > 0) {
            int viewStateStart = pageRetrieved.indexOf("__VIEWSTATE") + 37;
            int viewAppStart = pageRetrieved.indexOf("/>", viewStateStart);
            this.viewState = pageRetrieved.substring(viewStateStart, viewAppStart - 2);
        } else {
            this.viewState = "";
        }
        if (pageRetrieved.indexOf("__EVENTVALIDATION") > 0) {
            int validationStart = pageRetrieved.indexOf("__EVENTVALIDATION") + 49;
            int validEnd = pageRetrieved.indexOf("/>", validationStart);
            this.eventValidation = pageRetrieved.substring(validationStart, validEnd - 2);
        } else {
            this.eventValidation = "";
        }
        if (pageRetrieved.indexOf("__VIEWSTATEENCRYPTED") > 0) {
            int viewStateEncryptedStart = pageRetrieved.indexOf("__VIEWSTATEENCRYPTED") + 55;
            int viewStateEncryptedEnd = pageRetrieved.indexOf("/>", viewStateEncryptedStart);
            this.viewStateEncrypted = pageRetrieved.substring(viewStateEncryptedStart, viewStateEncryptedEnd - 2);
        } else {
            this.viewStateEncrypted = "";
        }
    }

    public void pageStart(String pageName) {
        this.currentPageName = pageName;
        this.pageStartTime = new java.util.Date().getTime();
        this.pageEndTime = this.pageStartTime;
        //PostLog.info("[Start -> " + pageName + "]");
    }

    public void pageEnd() {
        this.pageEndTime = new java.util.Date().getTime();
        this.pageTimeTaken = this.pageEndTime - this.pageStartTime;
        //PostLog.info("[End -> " + this.currentPageName + " (" + this.pageTimeTaken + "ms)]");
    }

    public void setHostConfig() {
        HostConfiguration hostConfig = client.getHostConfiguration();
        hostConfig.setHost("www.dvlottery.state.gov", 443, "https");
    }

    public int retrievePage(String url) {

        int statusCode = -1;
        setHostConfig();
        method = new GetMethod(url);
        pageStart("Retrieve : " + url);

        method.setRequestHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; YComp 5.0.2.6; Hotbar 4.3.5.0");
        method.setFollowRedirects(true);

        try {
            // execute the method.
            statusCode = client.executeMethod(method);
            responseBody = method.getResponseBody();
            pageRetrieved = new String(responseBody);
            //Lets save the __VIEWSTATE parameter && __EVENTVALIDATION values
            setViewState(pageRetrieved);

        } catch (HttpRecoverableException e) {
            PostLog.error("A recoverable exception occurred, retrying." + e.getMessage());
        } catch (IOException e) {
            PostLog.error("Failed to get " + url + "  -> " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            PostLog.error("Exception while retrieving " + url + " -> " + e.getMessage());
            e.printStackTrace();
        } finally {
            method.releaseConnection();
        }
        pageEnd();
        return statusCode;
    }

    public int postData(MultipartPostMethod post, String type, String url) {

        int statusCode = -1;
        post.setUseExpectHeader(true);
        post.setRequestHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; YComp 5.0.2.6; Hotbar 4.3.5.0");
        post.addParameter("__VIEWSTATE", this.viewState);
        post.addParameter("__EVENTVALIDATION", this.eventValidation);
        post.addParameter("__VIEWSTATEENCRYPTED", this.viewStateEncrypted);
        setHostConfig();
        try {
            Header[] h = post.getRequestHeaders();

            for (Header header : h) {
                PostLog.info("HeaderName = " + header.getName() + "Header Value = " + header.getValue());
            }

            statusCode = client.executeMethod(post);
            InputStream responseBodyStream = post.getResponseBodyAsStream();
            responseBody = IOUtils.toByteArray(responseBodyStream);
            //responsePage = convertStreamToString(responseBodyStream);
            //ByteArrayInputStream bais = (ByteArrayInputStream) responseBodyStream;
            //responseBody = responsePage.getBytes();
            //responseBody = new byte[]{0,1,1};
            //responsePage = IOUtils.toString(responseBodyStream);
            responsePage = new String(responseBody);
            //responseBody = post.getResponseBody();
            //PostLog.info(responsePage);
            setViewState(responsePage);
        } catch (Exception e) {
            PostLog.info("Exception while posting : " + e.getMessage());
            e.printStackTrace();
        } finally {
            post.releaseConnection();
        }
        pageEnd();
        return statusCode;
    }

    public int submitReviewPage(String url, String where) {
        int statusCode = 0;
        multiPart = new MultipartPostMethod(url);
        multiPart.setFollowRedirects(true);
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:btnContinueP2", "Submit");
        statusCode = postData(multiPart, where, url);
        if ((statusCode == -1) || (statusCode != 200)) {
            setErrorNo(ERROR_PAGE1_SUBMIT_FAILED);
            setSubmitStatus(APP_SUBMIT_FAILED);
            savePageAs("SubmitAppFailed.html", responseBody);
            PostLog.info("Failed to submit Review Page...");
        } else {
            /*
             * This is the Captcha Page
             * Call the specific method to handle Captcha
             *
             */
        }
        return statusCode;
    }

    /*
     public int submitApp(String url, String where, String appType) {
     int statusCode = 0;
     multiPart = new MultipartPostMethod(url);
     multiPart.addParameter("_ctl0:ContentPlaceHolder1:btnSubmit", "Submit Entry");
     statusCode = postData(multiPart, where, url);
     if ((statusCode == -1) || (statusCode != 200)) {
     setErrorNo(ERROR_PAGE1_SUBMIT_FAILED);
     setSubmitStatus(APP_SUBMIT_FAILED);
     savePageAs("SubmitAppFailed.html", responseBody);
     PostLog.info("Failed to submit Application...");
     } else {
     if (responsePage.indexOf("Submission Confirmation:") > 0) {
     savePageAs("submit/" + fileBaseName + ".html", responseBody);
     if (updateSubmitInfo(thisApp.getUserId(), responseBody, appType)) {
     PostLog.info("Updated confirmation number in database...Sleeping for 25 seconds");
     try {
     Thread.sleep(300);
     } catch (Exception e1) {
     e1.printStackTrace();
     }

     }
     PostLog.info("Submitted Application Successfully....");
     } else {
     savePageAs("submit/" + fileBaseName + "_error.html", responseBody);
     }
     }
     return statusCode;
     }
     */
    public boolean confirmSubmission(String appType) {
        boolean submissionConfirmed = false;
        if (responsePage.indexOf("Submission Confirmation:") > 0) {
            savePageAs("submit/" + fileBaseName + ".html", responseBody);
            if (updateSubmitInfo(thisApp.getUserId(), responseBody, appType)) {
                PostLog.info("Updated confirmation number in database...Sleeping for 25 seconds");
                try {
                    Thread.sleep(300);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            PostLog.info("Submitted Application Successfully....");
            submissionConfirmed = true;
        } else {
            savePageAs("submit/" + fileBaseName + "_error.html", responseBody);
        }
        return submissionConfirmed;
    }

    /**
     * Method to cancel the application submit process. This method would be
     * called if any errors are encountered while submitting. Errors may be
     * because of insufficient data populated etc. This is a two step process,
     * which involves posting a cancel request with the session values
     * "__VIEWSTATE" and "__EVENTVALIDATION" passed, receive a "Confirm cancel"
     * Page and again post a "Confirm Cancel" request and verify the
     * cancellation was successful.
     *
     * @param url The url where Cancel post has to be done.
     * @param where A pass through parameter for debugging information in logs,
     * passed on the postData method which makes use if it. where = which
     * place/state is the code being executed
     * @return -1 if posting failed, http response code from host server after
     * the post otherwise
     */
    public int cancelApp(String url, String where) {
        multiPart = new MultipartPostMethod(url);
        multiPart.addParameter("_ctl0:ContentPlaceHolder1:btnCancel", "Cancel Entry");
        int statusCode = postData(multiPart, where, url);
        if ((statusCode == -1) || (statusCode != 200)) {
            setErrorNo(ERROR_PAGE1_SUBMIT_FAILED);
            setSubmitStatus(APP_SUBMIT_FAILED);
            savePageAs("cancel/" + fileBaseName + "-Cancelstep1failed.html", responseBody);
            PostLog.info("Failed to cancel Application - Stage 1.");
        } else {
            // Lets confirm the cancellation of application
            multiPart = new MultipartPostMethod(url);
            multiPart.addParameter("_ctl0:ContentPlaceHolder1:spConfirmCancel:btnConfirm", "Confirm Cancellation");
            statusCode = postData(multiPart, "CONFIRM CANCEL ", url);
            if ((statusCode == -1) || (statusCode != 200)) {
                setErrorNo(ERROR_PAGE1_SUBMIT_FAILED);
                setSubmitStatus(APP_SUBMIT_FAILED);
                savePageAs("cancel/" + fileBaseName + "-Cancelstep2failed.html", responseBody);
                PostLog.info("Failed to cancel application - Stage 2 - confirmation");
            } else {
                PostLog.info("Cancellation success....");
                savePageAs("cancel/" + fileBaseName + "-cancelresponse.html", responseBody);
            }
        }
        return statusCode;
    }

    /**
     * Save the response received from host server as a byte array into flatfile
     *
     * @param fileName Name of flat file to be created
     * @param bytesToWrite The byte array to be written - response
     */
    public void savePageAs(String fileName, byte[] bytesToWrite) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fileName);
            fos.write(bytesToWrite);
        } catch (Exception e) {
            PostLog.error("Error saving " + fileName + "  : " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                    //PostLog.info("Saved " + fileName);
                } catch (Exception e) {
                    PostLog.info("Exception while closing file " + fileName + " : " + e.getMessage());
                }
            }
        }
    }

    /**
     * Update SubmitStatus table with confirmation number from the response
     * received against submit application post
     *
     * @param userId UserId of the application submitted
     * @param response Response received against submit application post
     * @param appType P -> Primary applicant, S -> Spouse. Applicable when
     * Couple Plan is opted
     * @return true if updated, false otherwise
     */
    public boolean updateSubmitInfo(int userId, byte[] response, String appType) {
        boolean updated = false;
        Connection conn = null;
        Statement stmt = null;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String submitYear = String.valueOf(currentYear);
        String confNumber = parseConfirmation(response, currentYear + 2);
        String sql = "insert into SubmitStatus values (" + userId + ",\"" + submitYear + "\",\"" + appType + "\",\"" + confNumber + "\")";
        System.err.println(sql);
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            if (stmt.executeUpdate(sql) == 1) {
                updated = true;
                if (mailToCustomer(confNumber, userId, appType)) {
                    PostLog.info("Mail Sent to the userId : " + userId + " App Type : " + appType + " Conf Number : " + confNumber);
                } else {
                    PostLog.info("[ERROR] in Sending Mail to the userId : " + userId + " App Type : " + appType + " Conf Number : " + confNumber);
                }
            }
        } catch (Exception e) {
            PostLog.info("Error while updating confirmation number for " + userId + "/" + appType + " : " + e.getMessage());
            e.printStackTrace();
        } finally {
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return updated;
    }

    /**
     * Parses the response received as a byte array to look for confirmation
     * number and returns the same
     *
     * @param response Response to submit application request as a byte[] array
     * @return Empty string if Confirmation number not found, Confirmation
     * number otherwise
     */
    public String parseConfirmation(byte[] response, int dvYear) {
        String confNumber = "";
        String responseString = new String(response);
        int offSetOfConfirmation = responseString.indexOf("Confirmation Number");
        int offSetConfirmationStart = 0;
        int offSetConfirmationEnd = 0;
        String confirmationNumberSubString = String.valueOf(dvYear);
        if (offSetOfConfirmation > 0) {
            offSetConfirmationStart = responseString.indexOf(confirmationNumberSubString, offSetOfConfirmation);
            offSetConfirmationEnd = responseString.indexOf("<", offSetConfirmationStart) - 1;
            confNumber = responseString.substring(offSetConfirmationStart, offSetConfirmationEnd + 1);
            if (confNumber == null) {
                confNumber = "";
            }
        }
        return confNumber.trim();
    }

    public boolean mailToCustomer(String confNumber, int userId, String appType) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        Mailer mail = new Mailer();
        UIMessageResource uiMessage = new UIMessageResource();
        uiMessage.getBundle();
        boolean sent = false;
        List mailArguments = new java.util.ArrayList();
        String toAddress = "";
        String firstName = "";
        String lastName = "";
        String subject = "";
        String body = "";
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select firstName, lastName, email from Registration where userId = " + userId);
            if (rs.next()) {
                mailArguments.add(rs.getString(1)); //arg 2 First Name
                mailArguments.add(rs.getString(2)); //arg 3 Last Name
                //toAddress = "sivak@dcis.net"; // Test Email Address
                toAddress = rs.getString(3); // User Email Address
                mailArguments.add(confNumber); //arg 4 Confirmation Number
                subject = uiMessage.getMessage("mail.content.subject");
                //System.err.println(">>> To Address >>>" + toAddress);
                if (appType.equals("P")) {
                    // Mail to the Applicant with Applicant's Conf Number
                    mail.mailUser("mail.sender", toAddress, subject, "mail.content.applicant", mailArguments);
                    sent = true;
                } else if (appType.equals("S")) {
                    // Mail to the Applicant with Applicant's Spouse's Conf Number
                    mail.mailUser("mail.sender", toAddress, subject, "mail.content.spouse", mailArguments);
                    sent = true;
                } else {
                    mail.mailUser("mail.sender", toAddress, subject, "mail.content.applicant", mailArguments);
                    sent = true;
                }
            }
        } catch (Exception e1) {
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return sent;
    }

    public URI getURI(MultipartPostMethod method) {
        URI uri = null;
        try {
            uri = method.getURI();

        } catch (URIException e) {
            e.printStackTrace();

        }
        //PostLog.info("URL >>> " + url);
        return uri;
    }

    public String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line + "\n");
        }
        is.close();
        return sb.toString();
    }

    public boolean pageContainsCaptcha() {
        //   PostLog.info("TEST HAS CAPTCHA >>>" + new String(responseBody));
        //   PostLog.info("TEST >>>> " + (new String(responseBody).contains(captchaProperties.getProperty("captcha.existence.text"))));
        if (new String(responseBody).contains(captchaProperties.getProperty("captcha.existence.text"))) {
            return true;
        }
        return false;
    }

    public boolean handleCaptcha(String appType) {
        PostLog.info("TEST HANDLE CHAPTCHA LOOP >>> " + (pageContainsCaptcha()));
        while (pageContainsCaptcha()) {
            PostLog.info("HAS CAPTCHA >>>>");
            CaptchaHandler c = new CaptchaHandler();
            boolean captchaStatus = c.handleCaptcha(this, appType);
            if (captchaStatus) {
                return captchaStatus;
            }
        }
        return false;
    }
}
