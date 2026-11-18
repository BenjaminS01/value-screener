package com.valuescreener.security.session;

import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * A {@link MapSession} plus what was stored in DynamoDB when it was loaded or last saved, so
 * {@link DynamoSessionRepository#save} can tell whether anything needs writing (the same wrapper pattern
 * as Spring Session's JDBC and Redis sessions). A brand-new session has no stored state.
 */
public final class DynamoSession implements Session {

    private final MapSession delegate;
    /** Id under which the session is stored; null while it was never stored. */
    String storedId;
    Instant storedLastAccessed;
    /** Serialized attributes as loaded or last saved. */
    byte[] storedAttributes;

    DynamoSession(MapSession delegate) {
        this.delegate = delegate;
    }

    boolean isStored() {
        return storedId != null;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String changeSessionId() {
        return delegate.changeSessionId();
    }

    @Override
    public <T> T getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Set<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
        delegate.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        delegate.removeAttribute(name);
    }

    @Override
    public Instant getCreationTime() {
        return delegate.getCreationTime();
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
        delegate.setLastAccessedTime(lastAccessedTime);
    }

    @Override
    public Instant getLastAccessedTime() {
        return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
        delegate.setMaxInactiveInterval(interval);
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return delegate.getMaxInactiveInterval();
    }

    @Override
    public boolean isExpired() {
        return delegate.isExpired();
    }
}
