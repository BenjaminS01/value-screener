package com.valuescreener.security.auth;

public record CsrfTokenView(String headerName, String token) {
}
