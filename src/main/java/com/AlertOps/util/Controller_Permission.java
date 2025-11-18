package com.AlertOps.util;

public class Controller_Permission {
    // Example: GET /users → READ_USER
    public static String mapToPermission(String method, String path) {
        if (method.equals("GET") && path.startsWith("/users")) return "READ_USER";
        if (method.equals("POST") && path.startsWith("/users")) return "WRITE_USER";
        if (method.equals("DELETE") && path.startsWith("/users")) return "DELETE_USER";
        if(method.equals("GET") && path.startsWith("/api/v1/tasks")) return  "read_task";
        if(method.equals("GET") && path.startsWith("/api/v1/task")) return "write_task";
        if(method.equals("POST") && path.equals("/api/v1/escalation")) return "create_flow";
        if(method.equals("GET") && path.equals("/api/v1/escalation")) return "read_flow";
        return "UNKNOWN";
    }

    public static Boolean isOpenRoute(String method, String path) { 
        if(method.equals("POST") && path.startsWith("/api/user")) return true;
        if(method.equals("POST") && path.startsWith("/api/v1/scheduler")) return true;
        return false;
    }

}
