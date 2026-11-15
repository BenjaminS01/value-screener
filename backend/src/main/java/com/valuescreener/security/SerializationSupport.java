package com.valuescreener.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;

/**
 * Java serialization for values that only this backend writes and reads (session attributes, throttle
 * state). Reading is restricted by an allow-list, so a manipulated item cannot instantiate arbitrary classes.
 */
final class SerializationSupport {

    /** JDK types, Spring Security types (security context, authentication, authorities) and our own. */
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "java.**;org.springframework.security.**;com.valuescreener.**;!*");

    private SerializationSupport() {
    }

    static byte[] serialize(Object value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(FILTER);
            return in.readObject();
        }
    }
}
