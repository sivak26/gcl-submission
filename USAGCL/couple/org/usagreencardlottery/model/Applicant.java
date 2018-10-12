package org.usagreencardlottery.model;
/**
 * 
 * @author sandeep
 * Class to hold values to be used for scrapping - One to one with site
 *
 */
public class Applicant{
    String lastName     = "";
    String noLastName   = "";
    String firstName    = "";
    String noFirstName  = "";
    String middleName   = "";
    String noMiddleName = "";
    String birthDay     = "";
    String birthMonth   = "";
    String birthYear    = "";
    String gender       = "";
    String birthCity    = "";
    String noBirthCity  = "";
    String birthCountry = "";
    String birthEligible   = "Y";
    String eligibleCountry = "0";
    String photoFileName   = "";
    String livingCountry="";
    String phoneNumber="";
    String email="";
    String education="";
    String maritalStatus="";
    String childCount="0";
	
	public String getBirthCity() {
		return birthCity;
	}
	public void setBirthCity(String birthCity) {
		this.birthCity = birthCity;
	}
	public String getBirthCountry() {
		return birthCountry;
	}
	public void setBirthCountry(String birthCountry) {
		this.birthCountry = birthCountry;
	}
	public String getBirthDay() {
		return birthDay;
	}
	public void setBirthDay(String birthDay) {
		this.birthDay = birthDay;
	}
	public String getBirthEligible() {
		return birthEligible;
	}
	public void setBirthEligible(String birthEligible) {
		this.birthEligible = birthEligible;
	}
	public String getBirthMonth() {
		return birthMonth;
	}
	public void setBirthMonth(String birthMonth) {
		this.birthMonth = birthMonth;
	}
	public String getBirthYear() {
		return birthYear;
	}
	public void setBirthYear(String birthYear) {
		this.birthYear = birthYear;
	}
	public String getChildCount() {
		return childCount;
	}
	public void setChildCount(String childCount) {
		this.childCount = childCount;
	}
	public String getEducation() {
		return education;
	}
	public void setEducation(String education) {
		this.education = education;
	}
	public String getEligibleCountry() {
		return eligibleCountry;
	}
	public void setEligibleCountry(String eligibleCountry) {
		this.eligibleCountry = eligibleCountry;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}
	public String getGender() {
		return gender;
	}
	public void setGender(String gender) {
		this.gender = gender;
	}
	public String getLastName() {
		return lastName;
	}
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	public String getLivingCountry() {
		return livingCountry;
	}
	public void setLivingCountry(String livingCountry) {
		this.livingCountry = livingCountry;
	}
	public String getMaritalStatus() {
		return maritalStatus;
	}
	public void setMaritalStatus(String maritalStatus) {
		this.maritalStatus = maritalStatus;
	}
	public String getMiddleName() {
		return middleName;
	}
	public void setMiddleName(String middleName) {
		this.middleName = middleName;
	}
	public String getNoBirthCity() {
		return noBirthCity;
	}
	public void setNoBirthCity(String noBirthCity) {
		this.noBirthCity = noBirthCity;
	}
	public String getNoFirstName() {
		return noFirstName;
	}
	public void setNoFirstName(String noFirstName) {
		this.noFirstName = noFirstName;
	}
	public String getNoLastName() {
		return noLastName;
	}
	public void setNoLastName(String noLastName) {
		this.noLastName = noLastName;
	}
	public String getNoMiddleName() {
		return noMiddleName;
	}
	public void setNoMiddleName(String noMiddleName) {
		this.noMiddleName = noMiddleName;
	}
	public String getPhoneNumber() {
		return phoneNumber;
	}
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	public String getPhotoFileName() {
		return photoFileName;
	}
	public void setPhotoFileName(String photoFileName) {
		this.photoFileName = photoFileName;
	}
}