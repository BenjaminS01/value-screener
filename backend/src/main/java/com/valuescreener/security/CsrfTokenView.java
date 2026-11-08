package com.valuescreener.security;

public record CsrfTokenView(String headerName, String token) {
}
