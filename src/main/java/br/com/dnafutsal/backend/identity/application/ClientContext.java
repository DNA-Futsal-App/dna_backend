package br.com.dnafutsal.backend.identity.application;

public record ClientContext(String userAgent, String ipAddress) {
    public ClientContext {
        userAgent = truncate(userAgent, 300);
        ipAddress = truncate(ipAddress, 64);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), max));
    }
}
