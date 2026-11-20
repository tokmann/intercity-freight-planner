package com.mirea.freight.model;

public record Driver(int driverId, String lastName, String firstName, String middleName, String phone, String licenseNumber) {

    public String shortName() {
        return lastName + " " + firstName.charAt(0) + ". " + middleName.charAt(0) + ".";
    }
}
