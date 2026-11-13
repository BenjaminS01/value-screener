package com.valuescreener.limits;

/** Unchecked on purpose: it must roll back the surrounding transaction. */
public class LimitReachedException extends RuntimeException {

    public enum Scope { ACCOUNT, GLOBAL }

    private final Scope scope;

    public LimitReachedException(Scope scope) {
        super("Daily limit reached: " + scope);
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }
}
