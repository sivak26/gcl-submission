package org.usagreencardlottery.model;

import java.util.ArrayList;

public class AppDetails {

    int userId = 0;
    boolean hasChildren = false;
    int childCount = 0;
    boolean couplePlan = false;
    String nativeMode = "";
    String nativeCountry = "";
    org.usagreencardlottery.model.Applicant applicant = new org.usagreencardlottery.model.Applicant();
    org.usagreencardlottery.model.Applicant spouse = new org.usagreencardlottery.model.Applicant();
    ArrayList children = null;
    ArrayList contact = null;
    String apartmentNumber = "";
    String street = "";
    String city = "";
    String state = "";
    String country = "";
    String zipcode = "";
    String nozipcode = "";
    String emailIdToContact = "";
    String firstNameCareOf = "";
    String lastNameCareOf = "";

    public org.usagreencardlottery.model.Applicant getApplicant() {
        return applicant;
    }

    public void setApplicant(org.usagreencardlottery.model.Applicant applicant) {
        this.applicant = applicant;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }

    public ArrayList getChildren() {
        return children;
    }

    public void setChildren(ArrayList children) {
        this.children = children;
    }

    public boolean isCouplePlan() {
        return couplePlan;
    }

    public void setCouplePlan(boolean couplePlan) {
        this.couplePlan = couplePlan;
    }

    public boolean isHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    public org.usagreencardlottery.model.Applicant getSpouse() {
        return spouse;
    }

    public void setSpouse(org.usagreencardlottery.model.Applicant spouse) {
        this.spouse = spouse;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getNativeCountry() {
        return nativeCountry;
    }

    public void setNativeCountry(String nativeCountry) {
        this.nativeCountry = nativeCountry;
    }

    public String getNativeMode() {
        return nativeMode;
    }

    public void setNativeMode(String nativeMode) {
        this.nativeMode = nativeMode;
    }

    public ArrayList getContact() {
        return contact;
    }

    public void setContact(ArrayList contact) {
        this.contact = contact;
    }

    public String getApartmentNumber() {
        return apartmentNumber;
    }

    public void setApartmentNumber(String apartmentNumber) {
        this.apartmentNumber = apartmentNumber;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getZipcode() {
        return zipcode;
    }

    public void setZipcode(String zipcode) {
        this.zipcode = zipcode;
    }

    public String getNoZipcode() {
        return nozipcode;
    }

    public void setNoZipcode(String nozipcode) {
        this.nozipcode = nozipcode;
    }

    public String getEmailIdToContact() {
        return emailIdToContact;
    }

    public void setEmailIdToContact(String emailIdToContact) {
        this.emailIdToContact = emailIdToContact;
    }

    public String getFirstNameCareOf() {
        return firstNameCareOf;
    }

    public void setFirstNameCareOf(String firstNameCareOf) {
        this.firstNameCareOf = firstNameCareOf;
    }

    public String getLastNameCareOf() {
        return lastNameCareOf;
    }

    public void setLastNameCareOf(String lastNameCareOf) {
        this.lastNameCareOf = lastNameCareOf;
    }
}