package com.valuescreener.limits;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class LimitsPropertiesTest {

    @Test
    void applicationYmlDefaultsBind() throws Exception {
        PropertySource<?> ps = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).get(0);
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(ps);

        LimitsProperties props = new Binder(ConfigurationPropertySources.get(env))
                .bind("app.limits", LimitsProperties.class).get();

        assertThat(props).isEqualTo(new LimitsProperties(5, 20, 100));
    }
}
