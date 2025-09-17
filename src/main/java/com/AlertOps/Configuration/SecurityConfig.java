

package com.AlertOps.Configuration;

import com.AlertOps.component.RbacAuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Autowired
    private RbacAuthorizationFilter rbacAuthorizationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
               .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                ).addFilterBefore(rbacAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}

