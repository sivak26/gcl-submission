package org.usagreencardlottery.main;

/**
 *
 * @author mageshwaran
 */
import com.oreilly.servlet.Base64Decoder;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import org.im4java.core.*;
import org.im4java.process.*;
import org.usagreencardlottery.model.*;
import org.usagreencardlottery.services.PostApplication;
import org.usagreencardlottery.utils.db.DBConnector;

public class ScrapperWithoutShutdownHook {

    public static DBConnector connector = new DBConnector();
    public static org.apache.log4j.Category ScrapLog = org.apache.log4j.Category.getInstance("org.usagreencardlottery.main.ScrapperWithoutShutdownHook");

    public static void main(String[] args) {

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        AppDetails thisApp = null;
        PostApplication thisPost = null;
        int counter = 0;
        String action = "";
        int userId = 0;
        int applicationId = 0;
        int applicationStatus = 0;
        String email = "";
        String comments = "";
        String additional = "";
        int winner = 0;
        int postCount = 0;
        String nativeMode = "";
        String nativeCountry = "";
        boolean needSubmission = true;
        //int maxSubmission = 1;
        int submittedTillNow = 0;
        String birthCountry = "";
        int endYear = 0;
        int startId = 0;
        int endId = 0;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String photoUploadCutOffDate = Integer.toString(currentYear - 1) + "-11-23 00:00:00";

        if (args.length > 0) {
            startId = Integer.parseInt(args[0]);
            endId = Integer.parseInt(args[1]);
        }

        ScrapLog.info("BEGINING THE PROCESS AT :: " + (new Date()).toString());
        connector.loadConfig("gclocal.properties");
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select Registration.userId, "
                    + "Application.applicationId, "
                    + "Application.applicationStatus, "
                    + "Registration.email, "
                    + "EligibilityHistory.endYear, "
                    + "Application.result2007, "
                    + "Registration.nativeMode, "
                    + "Registration.nativeCountry, "
                    + "Registration.birthCountry, "
                    + "Applicant.maritalStatus "
                    + "from Application, Registration, EligibilityHistory, Contact, Applicant "
                    + "where Registration.is_eligible = 1 and "
                    + "Application.applicationStatus = 10 and "
                    + "EligibilityHistory.endYear >= " + currentYear + " and "
                    + "(Application.post2009 is NULL or Application.post2009 != 20) and "
                    + "Registration.userId = Application.userId and "
                    + "Registration.userId = EligibilityHistory.userId and "
                    + "Registration.userId = Contact.userId and "
                    + "Application.applicationId = Applicant.applicationId and "
                    + "Registration.invalid_emailID = 0 and Registration.email not like \"testpayment%\" and "
                    + "Registration.email not like \"%-deleted@%\" and "
                    + "Registration.email not like \"%-cancelled@%\" and "
                    + "Registration.userId >= " + startId + " and Registration.userId <= " + endId);
            while (rs.next()) {
                if (rs.getInt(10) != 2) {
                    ScrapLog.info("This applicant's marital status is : " + getApplicantMaritalStatus(rs.getInt(10)));
                    counter++;
                    userId = rs.getInt(1);
                    if ((rs.getString(9) != null) && (!"null".equals(rs.getString(9))) && (!rs.getString(9).equals(""))) {
                        applicationId = rs.getInt(2);
                        applicationStatus = rs.getInt(3);
                        email = rs.getString(4);
                        endYear = rs.getInt(5);
                        winner = rs.getInt(6);
                        nativeMode = rs.getString(7);
                        nativeCountry = rs.getString(8);
                        birthCountry = rs.getString(9);
                        comments = "";
                        additional = "";

                        // Submit an application only if,
                        // the payment-plan not expired
                        // not already a winner
                        // there is a payment connected to this application
                        // is the application not in the submisstion skip list
                        if (isPaymentConnected(applicationId)
                                && endYear >= currentYear
                                && winner == 0
                                && isLatestPhotoUploaded(userId, photoUploadCutOffDate)
                                && isNotInSkipList(userId)) {
                            comments = "Need to service";
                            additional = "All Applications whose Validity is " + currentYear + "  OR  Longer Than " + currentYear;
                        }

                        if (comments.equals("Need to service")) {
                            ScrapLog.info("PROCESS STARTED FOR THE ACCOUNT ID :: " + userId);
                            if (applicationStatus == 10) {
                                thisApp = getAppDetails(userId, applicationId, nativeMode, nativeCountry);
                                thisApp.getApplicant().setBirthCountry(birthCountry);

                                // BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
                                boolean isApplicantMaritalStatusKnown = !thisApp.getApplicant().getMaritalStatus().equals("UNKNOWN");
                                boolean isSpouseIncluded = thisApp.isCouplePlan() && thisApp.getApplicant().getMaritalStatus().equals("6");
                                // END NEW CHANGES BASED ON THE MARITAL STATUS

                                if (isSpouseIncluded) {
                                    ScrapLog.info("SPOUSE INCLUDED IN THIS APPLICATION - PRIMARY and SECONDARY APPLICATION TO BE SUBMITTED");
                                } else {
                                    ScrapLog.info("ONLY PRIMARY APPLICATION TO BE SUBMITTED");
                                }
                                ScrapLog.info("NEED TO CHECK IF THIS APPLICATION WAS ALREADY SUBMITTED");

                                boolean submitPrimary = (getSubmissionStatus(applicationId) < 19);
                                boolean submitSpouse = isSpouseIncluded && getSubmissionStatus(applicationId) != 20;

                                //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
                                needSubmission = isApplicantMaritalStatusKnown && (submitPrimary || submitSpouse);
                                //END NEW CHANGES BASED ON THE MARITAL STATUS

                                if (thisApp.getApplicant().getLivingCountry() == null
                                        || thisApp.getApplicant().getLivingCountry().equals("null")
                                        || thisApp.getApplicant().getLivingCountry().equals("")) {

                                    needSubmission = false;
                                    action = "[CRITICAL] Living Country is empty - So not considering";
                                }

                                //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
                                if (!needSubmission) {
                                    action = "SUBMISSION NOT REQUIRED";
                                    if (getSubmissionStatus(applicationId) > 18) {
                                        ScrapLog.info("APPLICATION WAS ALREADY SUBMITTED | " + postCount);
                                    } else if (!isApplicantMaritalStatusKnown) {
                                        ScrapLog.info("APPLICANT MARITAL STATUS UNKNOWN FOR THE USER WITH USER-ID : " + userId);
                                    } else {
                                        ScrapLog.info("Living Country is empty");
                                    }
                                    //END NEW CHANGES BASED ON THE MARITAL STATUS

                                } else {

                                    action = "NEED TO SUBMIT [" + thisApp.isCouplePlan() + "/" + postCount + "] -> ";
                                    thisPost = new PostApplication(thisApp);
                                    if (submitPrimary) {
                                        ScrapLog.info("ATTEMPTING TO SUBMIT THE PRIMARY APPLICATION");
                                        if (thisPost.submitApplication("P")) {
                                            ScrapLog.info("Primary Applicant submitted...");
                                            ScrapLog.info("PLEASE WAIT WHILE WE ARE SAVING THE APPLICANT'S SUBMISSION STATUS TO OUR SYSTEM....");
                                            updatePost(thisApp.getUserId(), 19);
                                            action = action + "PRIMARY SUBMITTED";
                                            postCount = 1;
                                        }
                                    } else {
                                        ScrapLog.info("PRIMARY APPLICATION WAS ALREADY SUBMITTED.  BUT SPOUSE APPLICATION WAS SKIPPED.");
                                    }
                                    if (submitSpouse) {
                                        if (thisApp.getSpouse().getEducation().equals("0") || thisApp.getSpouse().getEducation().equals("null")) {
                                            ScrapLog.info("[CRITICAL] Since spouse education info is not complete will not attempt submission...");
                                            action = action + "/ SPOUSE SUBMISSION SKIPPED";
                                        } else {
                                            ScrapLog.info("ATTEMPTING TO SUBMIT THE SPOUSE APPLICATION");
                                            if (thisPost.submitApplication("S")) {
                                                ScrapLog.info("PLEASE WAIT WHILE WE ARE SAVING THE SPOUSE'S SUBMISSION STATUS TO OUR SYSTEM....");
                                                updatePost(thisApp.getUserId(), 20);
                                                action = action + "/ SPOUSE SUBMITTED";
                                                postCount = 2;
                                                ScrapLog.info("Spouse Application submitted...");
                                            }
                                        }
                                    }
                                    if (postCount > 0) {
                                        submittedTillNow++;
                                    }
                                    ScrapLog.info("TOTAL APPLICATIONS SUBMITTED FROM YOUR CURRENT RANGE :: " + submittedTillNow);
                                    action = action + "|" + postCount;

                                }//End of else
                            } else {
                                action = "NOT TAKING FOR SUBMISSION | " + appStatus(applicationStatus);
                            }
                        } else {
                            action = "NOT CONSIDERING FOR SUBMISSION | " + appStatus(applicationStatus);
                        }
                        ScrapLog.info(counter + "|" + "|" + userId + "|" + applicationId + "|" + email + "|" + action + "|" + additional);
                    } else {
                        ScrapLog.info("[IGNORED] " + userId + "/" + rs.getString(9) + "/");
                    }
                } else {
                    ScrapLog.info("This applicant's marital status is : " + getApplicantMaritalStatus(rs.getInt(10)));
                }
            }
            ScrapLog.info("EITHER THE APPLICATION(S) GOT ALREADY SUBMITTED | OR | YOU WANTED TO QUIT FOR A BREAK | OR | END OF LOOP");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
    }

    public static String getApplicantMaritalStatus(int status) {
        String strStatus = "Unknown";
        switch (status) {
            case 0:
                strStatus = "Unknown";
                break;
            case 1:
                strStatus = "Single";
                break;
            case 2:
                strStatus = "Married";
                break;
            case 3:
                strStatus = "Divorced";
                break;
            case 4:
                strStatus = "Widowed";
                break;
            case 5:
                strStatus = "Legally Separated";
                break;
        }
        return strStatus;
    }

    public static String appStatus(int status) {
        String strStatus = "Unknown";
        switch (status) {
            case 0:
                strStatus = "Awaiting Payment";
                break;
            case 1:
                strStatus = "Payment Rejected";
                break;
            case 2:
                strStatus = "Unpaid";
                break;
            case 3:
                strStatus = "Application Paid";
                break;
            case 4:
                strStatus = "Application Started";
                break;
            case 5:
                strStatus = "In Progress";
                break;
            case 6:
                strStatus = "Awaiting Photos";
                break;
            case 7:
                strStatus = "Incomplete";
                break;
            case 8:
                strStatus = "User completed";
                break;
            case 9:
                strStatus = "Selected for review";
                break;
            case 10:
                strStatus = "Ready for submission";
                break;
            case 11:
                strStatus = "One year plan and not a winner";
                break;
            case 12:
                strStatus = "Not submitted and was incomplete";
                break;
            case 13:
                strStatus = "Review complete, but not submitted";
                break;
            case 14:
                strStatus = "Multi year plan loser";
                break;
            case 15:
                strStatus = "Winner";
                break;
            case 20:
                strStatus = "Photo rejected";
                break;
        }
        return strStatus;
    }

    public static void updatePost(int userId, int count) {
        Connection conn = null;
        Statement stmt = null;
        int rowsUpdated = 0;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rowsUpdated = stmt.executeUpdate("update Application set post2009=" + count + " where userId=" + userId);
        } catch (Exception e) {
            ScrapLog.info("[CRITICAL] Error updating post count for " + userId + " with " + count + " -> " + e.getMessage());
        } finally {
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        if (rowsUpdated == 0) {
            ScrapLog.info("[CRITICAL] Post count not updated for " + userId + " with " + count);
        }
    }

    public static AppDetails getAppDetails(int userId, int appId, String nativeMode, String nativeCountry) {
        AppDetails thisApp = new AppDetails();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        String birthDate = null;
        String couplePlans = "-4-5-6-12-13-14-19-20-21-25-26-27-30-31-32-41-42-43-79-80-81-89-90-91-7-8-15-28-29-37-44-65-66-67-68-69-70-74-78-82-84-88-92-95-98-101-104-109-";
        String plan = "1";
        String[] dateTokens = null;

        thisApp.setNativeCountry(nativeCountry);
        thisApp.setNativeMode(nativeMode);
        thisApp.setUserId(userId);

        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select * from Applicant where applicationId=" + appId);
            if (rs.next()) {

                thisApp.getApplicant().setFirstName(rs.getString(3));
                if (thisApp.getApplicant().getFirstName().trim().equals("") || thisApp.getApplicant().getFirstName().trim().equals("null")) {
                    thisApp.getApplicant().setNoFirstName("on");
                    thisApp.getApplicant().setFirstName("");
                }

                thisApp.getApplicant().setLastName(rs.getString(4));
                if (thisApp.getApplicant().getLastName().trim().equals("") || thisApp.getApplicant().getLastName().trim().equals("null")) {
                    thisApp.getApplicant().setNoLastName("on");
                    thisApp.getApplicant().setLastName("");
                }

                thisApp.getApplicant().setMiddleName(rs.getString(5));
                if (thisApp.getApplicant().getMiddleName().trim().equals("") || thisApp.getApplicant().getMiddleName().trim().equals("null")) {
                    thisApp.getApplicant().setNoMiddleName("on");
                    thisApp.getApplicant().setMiddleName("");
                }

                if (rs.getInt(6) == 1) {
                    thisApp.getApplicant().setGender("rdoGenderM");
                    thisApp.getSpouse().setGender("rdoGenderF");
                } else {
                    thisApp.getApplicant().setGender("rdoGenderF");
                    thisApp.getSpouse().setGender("rdoGenderM");
                }

                //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
                int maritalStatus = rs.getInt(7);

                if (maritalStatus == 2) {
                    if ((Boolean) rs.getObject(16) != null) {
                        if ((Boolean) rs.getObject(16)) {
                            thisApp.getApplicant().setMaritalStatus("2");
                            thisApp.getSpouse().setMaritalStatus("6");
                        } else {
                            thisApp.getApplicant().setMaritalStatus("6");
                            thisApp.getSpouse().setMaritalStatus("6");
                        }
                    } else {
                        //For the users who did not complete the Marital Status
                        //we decided to set their marital status as
                        //I am married and my spouse is NOT a US citizen
                        thisApp.getApplicant().setMaritalStatus("6");
                        thisApp.getSpouse().setMaritalStatus("6");
                    }
                } else {
                    thisApp.getApplicant().setMaritalStatus(Integer.toString(rs.getInt(7)));
                    thisApp.getSpouse().setMaritalStatus(Integer.toString(rs.getInt(7)));
                }
                //END NEW CHANGES BASED ON THE MARITAL STATUS

                thisApp.getApplicant().setEducation(Integer.toString(rs.getInt(8)));

                thisApp.getApplicant().setChildCount(Integer.toString(rs.getInt(9)));
                thisApp.getSpouse().setChildCount(Integer.toString(rs.getInt(9)));
                thisApp.setChildCount(rs.getInt(9));

                birthDate = rs.getString(10);
                dateTokens = getDateTokens(birthDate);

                thisApp.getApplicant().setBirthYear(dateTokens[0]);
                thisApp.getApplicant().setBirthMonth(dateTokens[1]);
                thisApp.getApplicant().setBirthDay(dateTokens[2]);

                thisApp.getApplicant().setBirthCity(rs.getString(11));
                thisApp.getApplicant().setBirthCountry(rs.getString(12));

                if (thisApp.getApplicant().getBirthCity().trim().equals("")
                        || thisApp.getApplicant().getBirthCity().trim().equals("null")) {

                    thisApp.getApplicant().setNoBirthCity("on");
                    thisApp.getApplicant().setBirthCity("");
                }

                if (nativeMode == "1") {
                    thisApp.getApplicant().setBirthEligible("Y");
                    thisApp.getApplicant().setEligibleCountry("0");
                    thisApp.getSpouse().setBirthEligible("N");
                    thisApp.getSpouse().setEligibleCountry(thisApp.getApplicant().getBirthCountry());
                } else {
                    thisApp.getApplicant().setBirthEligible("N");
                    thisApp.getApplicant().setEligibleCountry(nativeCountry);
                    if (nativeMode == "2") {
                        thisApp.getSpouse().setBirthEligible("Y");
                        thisApp.getSpouse().setEligibleCountry("0");
                    } else {
                        thisApp.getSpouse().setBirthEligible("N");
                        thisApp.getSpouse().setEligibleCountry(nativeCountry);
                    }
                }

                thisApp.getApplicant().setPhotoFileName(getPhoto(rs.getInt(15)));
                thisApp.getApplicant().setLivingCountry(getLivingCountry(appId));
                thisApp.getSpouse().setLivingCountry(getLivingCountry(appId));

                thisApp.getApplicant().setPhoneNumber("");
                thisApp.getSpouse().setPhoneNumber("");

                thisApp.getApplicant().setEmail("");
                thisApp.getSpouse().setEmail("");

                if (thisApp.getChildCount() > 0) {
                    thisApp.setChildren(getChildren(appId));
                }
                thisApp.setContact(getContact(appId, userId));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select productId from Application where applicationId=" + appId);
            if (rs.next()) {
                plan = Integer.toString(rs.getInt(1));
                if (couplePlans.contains("-" + plan + "-")) {
                    thisApp.setCouplePlan(true);
                } else {
                    thisApp.setCouplePlan(false);
                }
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }

        //BEGIN NEW CHANGES BASED ON THE MARITAL STATUS
        if (thisApp.getApplicant().getMaritalStatus().equals("2") || thisApp.getApplicant().getMaritalStatus().equals("6")) {
            //END NEW CHANGES BASED ON THE MARITAL STATUS

            Applicant thisSpouse = thisApp.getSpouse();
            try {
                conn = connector.getConnection();
                stmt = conn.createStatement();
                rs = stmt.executeQuery("select * from Dependents where applicationId=" + appId + " and dependentType=1");
                if (rs.next()) {

                    thisSpouse.setFirstName(rs.getString(4));
                    if (thisSpouse.getFirstName().trim().equals("") || thisSpouse.getFirstName().trim().equals("null")) {
                        thisSpouse.setNoFirstName("on");
                        thisSpouse.setFirstName("");
                    }

                    thisSpouse.setLastName(rs.getString(5));
                    if (thisSpouse.getLastName().trim().equals("") || thisSpouse.getLastName().trim().equals("null")) {
                        thisSpouse.setNoLastName("on");
                        thisSpouse.setLastName("");
                    }

                    thisSpouse.setMiddleName(rs.getString(6));
                    if (thisSpouse.getMiddleName().trim().equals("") || thisSpouse.getMiddleName().trim().equals("null")) {
                        thisSpouse.setNoMiddleName("on");
                        thisSpouse.setMiddleName("");
                    }

                    birthDate = rs.getString(8);
                    dateTokens = getDateTokens(birthDate);
                    thisSpouse.setBirthYear(dateTokens[0]);
                    thisSpouse.setBirthMonth(dateTokens[1]);
                    thisSpouse.setBirthDay(dateTokens[2]);

                    thisSpouse.setBirthCity(rs.getString(9));
                    thisSpouse.setBirthCountry(rs.getString(10));

                    if (thisSpouse.getBirthCity().trim().equals("") || thisSpouse.getBirthCity().trim().equals("null")) {
                        thisSpouse.setNoBirthCity("on");
                        thisSpouse.setBirthCity("");
                    }
                    thisSpouse.setEducation(Integer.toString(rs.getInt(11)));
                    ScrapLog.info("user id = " + userId + " Spouse education = " + rs.getInt(11));
                    thisSpouse.setPhotoFileName(getPhoto(rs.getInt(12)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                connector.closeResultSet(rs);
                connector.closeStatement(stmt);
                connector.closeConnection(conn);
            }
        }
        return thisApp;
    }

    public static String getPhoto(int photoId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        String fileNameBeforeDPI = "photos/" + Integer.toString(photoId) + "-beforeDPI.jpg";
        String fileName = "photos/" + Integer.toString(photoId) + ".jpg";
        FileOutputStream fos = null;
        FileInputStream fis = null;
        FileOutputStream fosDPI = null;
        FileInputStream fisDPI = null;
        InputStream data = null;
        int bytesRead = 0;
        ConvertCmd cmd = new ConvertCmd();
        ImageCommand ic = new ImageCommand();

        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select photograph from Photographs where photoId=" + photoId);
            if (rs.next()) {
                data = new Base64Decoder(rs.getAsciiStream(1));
                fos = new FileOutputStream(fileNameBeforeDPI);
                while ((bytesRead = data.read()) != -1) {
                    fos.write(bytesRead);
                }
                fos.flush();
                fos.close();
                data.close();
                // Start DPI Conversion
                fisDPI = new FileInputStream(fileNameBeforeDPI);
                fosDPI = new FileOutputStream(fileName);
                Pipe pipe = new Pipe(fisDPI, fosDPI);
                IMOperation op = new IMOperation();
                op.addImage("-");
                op.density(new Integer(300));
                op.quality(97.0);
                op.addImage("-");
                cmd.setInputProvider(pipe);
                cmd.setOutputConsumer(pipe);
                cmd.run(op);
                fosDPI.flush();
                fosDPI.close();
                // End DPI Conversion
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return fileName;
    }

    public static String getLivingCountry(int appId) {
        String country = "";
        Statement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select Contact.country, Payment.billingCountry from Payment, Contact where Contact.applicationId=" + appId + " and Payment.ApplicationId=Contact.applicationID");
            if (rs.next()) {
                country = rs.getString(1);
                if (null == country || country.equals("")) {
                    country = rs.getString(2);
                    if (country.equals("US")) {
                        country = "223";
                    } else if (country.equals("CA")) {
                        country = "221";
                    } else {
                        country = null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return country;
    }

    public static boolean isPaymentConnected(int appId) {
        boolean paymentConnected = false;
        Statement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select * from Payment where Payment.applicationId = " + appId + " order by Payment.paymentId desc limit 1");
            if (rs.next()) {
                paymentConnected = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return paymentConnected;
    }

    public static boolean isLatestPhotoUploaded(int userId, String photoUploadCutOffDate) {
        boolean latestPhotoUploaded = true;
        Statement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select uploadedDate from Photographs where Photographs.userId = " + userId + " and Photographs.uploadedDate < \"" + photoUploadCutOffDate + "\"");
            if (rs.next()) {
                latestPhotoUploaded = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return latestPhotoUploaded;
    }

    public static boolean isNotInSkipList(int userId) {
        boolean notInSkipList = true;
        Statement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select userId from SkipList where SkipList.userId = " + userId);
            if (rs.next()) {
                notInSkipList = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return notInSkipList;
    }

    /*
     public static Applicant getSpouse(int appId){
     Applicant thisSpouse = new Applicant();
     Connection conn = null;
     Statement stmt = null;
     ResultSet rs = null;
     String birthDate = null;
     String[] dateTokens=null;


     return thisSpouse;
     }
     */
    public static ArrayList getChildren(int appId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        String birthDate = null;
        String[] dateTokens = null;
        Child thisChild = null;
        ArrayList<Child> children = new ArrayList<Child>();

        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select * from Dependents where applicationId=" + appId + " and dependentType=2");
            while (rs.next()) {
                thisChild = new Child();

                thisChild.setFirstName(rs.getString(4));
                if (thisChild.getFirstName().trim().equals("") || thisChild.getFirstName().trim().equals("null")) {
                    thisChild.setNoFirstName("on");
                    thisChild.setFirstName("");
                }

                thisChild.setLastName(rs.getString(5));
                if (thisChild.getLastName().trim().equals("") || thisChild.getLastName().trim().equals("null")) {
                    thisChild.setNoLastName("on");
                    thisChild.setLastName("");
                }

                thisChild.setMiddleName(rs.getString(6));
                if (thisChild.getMiddleName().trim().equals("") || thisChild.getMiddleName().trim().equals("null")) {
                    thisChild.setNoMiddleName("on");
                    thisChild.setMiddleName("");
                }

                if (rs.getInt(7) == 1) {
                    thisChild.setGender("rdoGenderM");
                } else {
                    thisChild.setGender("rdoGenderF");
                }
                birthDate = rs.getString(8);
                dateTokens = getDateTokens(birthDate);
                thisChild.setBirthYear(dateTokens[0]);
                thisChild.setBirthMonth(dateTokens[1]);
                thisChild.setBirthDay(dateTokens[2]);
                thisChild.setBirthCity(rs.getString(9));
                thisChild.setBirthCountry(rs.getString(10));

                if (thisChild.getBirthCity().trim().equals("") || thisChild.getBirthCity().trim().equals("null")) {
                    thisChild.setNoBirthCity("on");
                    thisChild.setBirthCity("");
                }
                thisChild.setPhotoFileName(getPhoto(rs.getInt(12)));
                children.add(thisChild);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return children;
    }

    // Contact Info - For Populating the Mailing Section
    public static ArrayList getContact(int appId, int userId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        AppDetails mailingContacts = null;
        ArrayList<AppDetails> contacts = new ArrayList<AppDetails>();
        String[] regInfo = null;

        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select * from Contact where applicationId=" + appId);
            while (rs.next()) {
                mailingContacts = new AppDetails();
                mailingContacts.setApartmentNumber(rs.getString(3));
                mailingContacts.setStreet(rs.getString(4));
                if (mailingContacts.getStreet().trim().equals("") || mailingContacts.getStreet().trim().equals("null")) {
                    mailingContacts.setStreet("");
                }
                mailingContacts.setCity(rs.getString(5));
                mailingContacts.setState(rs.getString(7));
                if (mailingContacts.getState().trim().equals("") || mailingContacts.getState().trim().equals("null")) {
                    mailingContacts.setState("");
                }
                mailingContacts.setCountry(rs.getString(8));
                mailingContacts.setZipcode(rs.getString(9));
                if (mailingContacts.getZipcode().trim().equals("") || mailingContacts.getZipcode().trim().equals("null") || mailingContacts.getZipcode() == null) {
                    mailingContacts.setZipcode("");
                    mailingContacts.setNoZipcode("on");
                }
                regInfo = getRegInfo(userId).split("///");
                mailingContacts.setFirstNameCareOf(regInfo[0]);
                mailingContacts.setLastNameCareOf(regInfo[1]);
                mailingContacts.setEmailIdToContact(regInfo[2]);
                contacts.add(mailingContacts);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return contacts;
    }

    public static String getRegInfo(int userId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        String regInfo = "";
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select firstName, LastName, email from Registration where userId=" + userId);
            while (rs.next()) {
                regInfo = rs.getString(1) + "///" + rs.getString(1) + "///" + rs.getString(3);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return regInfo;
    }

    public static String[] getDateTokens(String sqlDateString) {
        String[] dateTime = sqlDateString.split(" ");
        String[] dateTokens = dateTime[0].split("-");
        dateTokens[1] = Integer.toString(Integer.parseInt(dateTokens[1]));
        dateTokens[2] = Integer.toString(Integer.parseInt(dateTokens[2]));
        return dateTokens;
    }

    public static int plans(int pId) {
        String one = "/1/4/9/12/16/19/22/25/32/35/36/38/41/47/49/51/52/53/54/55/71/72/73/75/79/83/85/89/93/96/99/102/105/107/110/";
        String two = "/2/5/10/13/17/20/23/26/31/34/39/42/45/48/50/56/57/58/59/76/80/86/90/94/97/100/103/108/";
        String four = "/3/6/11/14/18/21/24/27/30/33/40/43/46/60/61/62/63/64/77/81/87/91/";
        String multi = "/7/8/15/28/29/37/44/65/66/67/68/69/70/74/78/82/84/88/92/95/98/101/104/109/";
        String strId = Integer.toString(pId);
        int plan = 1;
        if (two.indexOf("/" + strId + "/") >= 0) {
            plan = 2;
        } else if (four.indexOf("/" + strId + "/") >= 0) {
            plan = 4;
        } else if (multi.indexOf("/" + strId + "/") >= 0) {
            plan = 5;
        }
        return plan;
    }

    public static int getSubmissionStatus(int appId) {
        int status = 0;
        Statement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            conn = connector.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select post2009 from Application where applicationId=" + appId + " order by applicationId desc limit 1");
            if (rs.next()) {
                status = rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.closeResultSet(rs);
            connector.closeStatement(stmt);
            connector.closeConnection(conn);
        }
        return status;
    }

}
