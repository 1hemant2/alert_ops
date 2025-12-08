package com.alertops.component;

import com.alertops.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.alertops.util.Controller_Permission;

import java.io.IOException;
import java.util.Set;

@Component
public class RbacAuthorizationFilter extends OncePerRequestFilter {
    @Autowired
    private UserService userService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

       // String username = request.getUserPrincipal().getName(); // from JWT or session
        String userName = "root";
        Set<String> permissions = userService.getUserPermissions(userName);

        String httpMethod = request.getMethod();        // GET, POST, DELETE
        String path = request.getRequestURI();          // /users, /orders

        String requiredPermission = Controller_Permission.mapToPermission(httpMethod, path);
        Boolean isOpenRoute = Controller_Permission.isOpenRoute(httpMethod, path);
        if (isOpenRoute || permissions.contains(requiredPermission) || permissions.contains("super_admin")) {
            filterChain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
        }
    }
}
