package io.github.songrongzhen.easyagent.rag.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class UrlSafetyValidator {

    private final List<String> allowedDomains;

    public UrlSafetyValidator(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null ? List.of() : allowedDomains;
    }

    /**
     * 校验 URL 是否允许由服务端请求。
     */
    public URI validate(String url) {
        URI uri = parseUri(url);
        validateScheme(uri);
        String host = validateHost(uri);
        validateAllowedDomains(host);
        validateResolvedAddresses(host);
        return uri;
    }

    private URI parseUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTML knowledge URL must not be blank");
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid HTML knowledge URL: " + url, e);
        }
    }

    private void validateScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http and https URLs are allowed for HTML knowledge loading");
        }
    }

    private String validateHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("HTML knowledge URL host is required");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost)
                || "0.0.0.0".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)) {
            throw new IllegalArgumentException("Local addresses are not allowed for HTML knowledge loading: " + host);
        }
        return normalizedHost;
    }

    private void validateAllowedDomains(String host) {
        List<String> configuredDomains = allowedDomains.stream()
                .filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.toLowerCase(Locale.ROOT).trim())
                .toList();
        if (configuredDomains.isEmpty()) {
            return;
        }

        boolean matched = configuredDomains.stream().anyMatch(domain -> matchesDomain(domain, host));
        if (!matched) {
            throw new IllegalArgumentException("HTML knowledge URL host is not in allowed-domains: " + host);
        }
    }

    private boolean matchesDomain(String pattern, String host) {
        if (!pattern.contains("*")) {
            return pattern.equals(host);
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE).matcher(host).matches();
    }

    private void validateResolvedAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve HTML knowledge URL host: " + host, e);
        }

        for (InetAddress address : addresses) {
            if (isUnsafeAddress(address)) {
                throw new IllegalArgumentException("Unsafe HTML knowledge URL address is not allowed: " + address.getHostAddress());
            }
        }
    }

    private boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUnsafeIpv4(address)
                || isUnsafeIpv6(address);
    }

    private boolean isUnsafeIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || first >= 224;
    }

    private boolean isUnsafeIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        return (first & 0xfe) == 0xfc;
    }
}
