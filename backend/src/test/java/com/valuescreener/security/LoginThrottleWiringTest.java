package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the production wiring without any test Clock override; catches bean cycles slices would hide. */
class LoginThrottleWiringTest {

    @Test
    void productionWiringRefreshesWithoutCircularDependency() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(PropertySourcesPlaceholderConfigurer.class, ClockConfig.class,
                    LoginThrottleWebConfig.class, LoginAttemptGuard.class, ClientIpResolver.class);
            context.refresh();
            assertThat(context.getBean(Clock.class)).isNotNull();
            assertThat(context.getBean(LoginAttemptGuard.class)).isNotNull();
            context.getBean(LoginThrottleWebConfig.class).addInterceptors(new InterceptorRegistry());
        }
    }
}
