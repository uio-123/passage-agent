package com.passage.agent.agent.policy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** Validates every initial and redirected destination before an outbound request. */
public final class UrlSafetyValidator {
    private final HostResolver resolver;
    private final int maxUrlLength;

    public UrlSafetyValidator(HostResolver resolver, int maxUrlLength) {
        this.resolver = resolver;
        this.maxUrlLength = maxUrlLength;
    }

    public void validate(URI uri) {
        if (uri == null || uri.toString().length() > maxUrlLength || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new ToolPolicyException(ToolPolicyError.UNSAFE_URL, "URL is not permitted by web-reader policy");
        }
        try {
            InetAddress[] addresses = resolver.resolve(uri.getHost());
            if (addresses.length == 0) {
                throw new ToolPolicyException(ToolPolicyError.UNSAFE_NETWORK_ADDRESS, "URL host did not resolve");
            }
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new ToolPolicyException(ToolPolicyError.UNSAFE_NETWORK_ADDRESS,
                            "URL host resolved to a restricted network address");
                }
            }
        } catch (UnknownHostException e) {
            throw new ToolPolicyException(ToolPolicyError.UNSAFE_NETWORK_ADDRESS, "URL host could not be resolved", e);
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] value = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(value[0]);
            int second = Byte.toUnsignedInt(value[1]);
            return !(first == 0 || first == 10 || first == 127 || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168)
                    || (first == 100 && second >= 64 && second <= 127));
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(value[0]);
            return !(first == 0xfd || first == 0xfc || (first == 0xfe && (Byte.toUnsignedInt(value[1]) & 0xc0) == 0x80));
        }
        return false;
    }
}
