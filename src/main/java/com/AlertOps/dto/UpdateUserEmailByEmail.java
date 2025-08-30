package com.AlertOps.dto;

public class UpdateUserEmailByEmail {
    private  String email;
    private  String newEmail;

    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return  this.email;
    }

    public  void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getNewEmail() {
        return  this.newEmail;
    }
}
