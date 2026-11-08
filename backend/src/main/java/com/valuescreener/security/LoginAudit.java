package com.valuescreener.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/** Login audit log (logger "security.login"). Never receives a password. */
final class LoginAudit {

    private static final Logger LOG = LoggerFactory.getLogger("security.login");

    /** Control characters (incl. CR/LF/TAB/DEL) plus the Unicode line/paragraph separators. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    private LoginAudit() {
    }

    static void success(String username, String ip) {
        LOG.info("login success user={} ip={}", sanitize(username), sanitize(ip));
    }

    static void failure(String username, String ip, String reason) {
        LOG.warn("login failure user={} ip={} reason={}", sanitize(username), sanitize(ip), sanitize(reason));
    }

    static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        String clean = CONTROL_CHARS.matcher(value).replaceAll("");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
