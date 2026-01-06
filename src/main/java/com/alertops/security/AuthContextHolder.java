package com.alertops.security;

public class AuthContextHolder {
    // Add ctx data to thread assigned by tomcat during request.
    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    public static void set(AuthContext ctx) {
        CONTEXT.set(ctx);
    }

    public static AuthContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
