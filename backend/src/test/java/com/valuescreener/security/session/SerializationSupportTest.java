package com.valuescreener.security.session;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;

import java.io.InvalidClassException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializationSupportTest {

    @Test
    void rejectsDeeplyNestedInput() {
        Map<String, Object> outer = new HashMap<>();
        Map<String, Object> current = outer;
        for (int i = 0; i < 100; i++) {
            Map<String, Object> next = new HashMap<>();
            current.put("n", next);
            current = next;
        }
        byte[] data = SerializationSupport.serialize(outer);
        assertThatThrownBy(() -> SerializationSupport.deserialize(data)).isInstanceOf(InvalidClassException.class);
    }

    @Test
    void rejectsOversizedInput() {
        HashMap<String, Object> big = new HashMap<>();
        big.put("x", "a".repeat(20_000));
        byte[] data = SerializationSupport.serialize(big);
        assertThatThrownBy(() -> SerializationSupport.deserialize(data)).isInstanceOf(InvalidClassException.class);
    }

    @Test
    void acceptsARealLoggedInSecurityContext() throws Exception {
        var principal = User.withUsername("alice").password("").roles("ADMIN").build();
        SecurityContext context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("SPRING_SECURITY_CONTEXT", context);

        @SuppressWarnings("unchecked")
        Map<String, Object> read = (Map<String, Object>) SerializationSupport.deserialize(
                SerializationSupport.serialize(attributes));

        SecurityContext back = (SecurityContext) read.get("SPRING_SECURITY_CONTEXT");
        assertThat(back.getAuthentication().getName()).isEqualTo("alice");
    }
}
