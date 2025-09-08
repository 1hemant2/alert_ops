package com.AlertOps.dto.user;

public class GetUser {
    private  String email;
    private  String userName;
    private Long id;

    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return  this.email;
    }

    public  void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return  this.userName;
    }

    public  void setId(Long id) {
        this.id = id;
    }

    public  Long getId() {
        return  this.id;
    }
}
