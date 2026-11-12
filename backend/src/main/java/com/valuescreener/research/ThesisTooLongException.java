package com.valuescreener.research;

/** A thesis text field would exceed its limit. Mapped to 422 with the fixed message. */
public class ThesisTooLongException extends RuntimeException {

    public ThesisTooLongException() {
        super("Thesis text too long");
    }
}
