package dev.yggskins.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Valida URLs de texturas y bloquea destinos internos para evitar SSRF.
 */
public final class UrlValidator {

    public static final int DEFAULT_MAX_LENGTH = 512;

    public record Result(boolean valid, boolean blocked, String reason) {
        public static Result ok() {
            return new Result(true, false, null);
        }

        public static Result invalid(String reason) {
            return new Result(false, false, reason);
        }

        public static Result blocked(String reason) {
            return new Result(false, true, reason);
        }
    }

    private UrlValidator() {
    }

    public static Result validate(String raw, List<String> allowedDomains) {
        return validate(raw, allowedDomains, DEFAULT_MAX_LENGTH);
    }

    public static Result validate(String raw, List<String> allowedDomains, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return Result.invalid("empty");
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            return Result.invalid("too-long");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return Result.invalid("malformed");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return Result.invalid("missing-scheme");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Result.invalid("bad-scheme");
        }
        if (uri.getUserInfo() != null) {
            return Result.blocked("userinfo");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Result.invalid("missing-host");
        }
        host = host.toLowerCase(Locale.ROOT);

        if (isLocalName(host)) {
            return Result.blocked("local-host");
        }

        if (allowedDomains != null && !allowedDomains.isEmpty() && !isAllowed(host, allowedDomains)) {
            return Result.blocked("domain-not-allowed");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternal(address)) {
                    return Result.blocked("internal-address");
                }
            }
        } catch (UnknownHostException e) {
            return Result.invalid("unknown-host");
        }

        return Result.ok();
    }

    private static boolean isAllowed(String host, List<String> allowedDomains) {
        for (String entry : allowedDomains) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String domain = entry.trim().toLowerCase(Locale.ROOT);
            if (domain.startsWith("*.")) {
                domain = domain.substring(2);
            }
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalName(String host) {
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        return h.equals("localhost")
                || h.endsWith(".localhost")
                || h.endsWith(".local")
                || h.equals("0.0.0.0")
                || h.equals("::1")
                || h.equals("::");
    }

    private static boolean isInternal(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocal(address);
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 16) {
            return false;
        }
        return (bytes[0] & 0xFE) == 0xFC;
    }
}
