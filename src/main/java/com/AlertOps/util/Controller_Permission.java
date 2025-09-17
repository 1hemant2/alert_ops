package com.AlertOps.util;

public class Controller_Permission {
    // Example: GET /users → READ_USER
    public static String mapToPermission(String method, String path) {
        if (method.equals("GET") && path.startsWith("/users")) return "READ_USER";
        if (method.equals("POST") && path.startsWith("/users")) return "WRITE_USER";
        if (method.equals("DELETE") && path.startsWith("/users")) return "DELETE_USER";
        if(method.equals("GET") && path.startsWith("/api/v1/tasks")) return  "READ_TASK";
        if(method.equals("GET") && path.startsWith("/api/v1/task")) return "READ_TASK";
        return "UNKNOWN";
    }

    public static Boolean isOpenRoute(String method, String path) {
        return method.equals("POST") && path.startsWith("/api/user");
    }

}
