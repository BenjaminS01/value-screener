package com.valuescreener.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the login throttle; the guard and resolver are required, a missing bean fails startup. */
@Configuration
public class LoginThrottleWebConfig implements WebMvcConfigurer {

    private final LoginAttemptGuard guard;
    private final ClientIpResolver ipResolver;

    public LoginThrottleWebConfig(LoginAttemptGuard guard, ClientIpResolver ipResolver) {
        this.guard = guard;
        this.ipResolver = ipResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginThrottleInterceptor(guard, ipResolver))
                .addPathPatterns("/api/auth/login");
    }
}
