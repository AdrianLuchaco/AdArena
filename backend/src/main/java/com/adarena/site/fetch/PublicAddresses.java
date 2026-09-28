package com.adarena.site.fetch;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * ¿Es una dirección de internet de verdad? Cuando AdArena lee la web de un proyecto, solo puede
 * conectarse a direcciones públicas. Si una web (o su DNS) apuntara a una dirección interna
 * (127.0.0.1, 10.x, 192.168.x, 169.254.169.254 —los metadatos del servidor en la nube—…), alguien
 * podría usar AdArena para leer cosas de nuestra red privada. Eso se llama SSRF y aquí se bloquea.
 */
public final class PublicAddresses {

    private PublicAddresses() {
    }

    public static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(bytes);
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] ip) {
        int a = ip[0] & 0xff;
        int b = ip[1] & 0xff;
        int c = ip[2] & 0xff;
        return !(a == 0                                   // 0.0.0.0/8      "esta red"
                || a == 10                                // 10.0.0.0/8     privada
                || a == 127                               // 127.0.0.0/8    este ordenador
                || (a == 100 && b >= 64 && b <= 127)      // 100.64.0.0/10  red interna de operadores (CGNAT)
                || (a == 169 && b == 254)                 // 169.254.0.0/16 enlace local y metadatos de la nube
                || (a == 172 && b >= 16 && b <= 31)       // 172.16.0.0/12  privada
                || (a == 192 && b == 0 && c == 0)         // 192.0.0.0/24   reservada
                || (a == 192 && b == 0 && c == 2)         // 192.0.2.0/24   documentación
                || (a == 192 && b == 88 && c == 99)       // 192.88.99.0/24 relé 6to4
                || (a == 192 && b == 168)                 // 192.168.0.0/16 privada
                || (a == 198 && (b == 18 || b == 19))     // 198.18.0.0/15  pruebas de rendimiento
                || (a == 198 && b == 51 && c == 100)      // 198.51.100.0/24 documentación
                || (a == 203 && b == 0 && c == 113)       // 203.0.113.0/24 documentación
                || a >= 224);                             // multicast, reservadas y difusión
    }

    private static boolean isPublicIpv6(byte[] ip) {
        int first = ip[0] & 0xff;
        int second = ip[1] & 0xff;
        // Direcciones que "esconden" una IPv4 dentro: se juzga la IPv4
        if (isIpv4Mapped(ip) || isIpv4Compatible(ip)) {
            return isPublicIpv4(Arrays.copyOfRange(ip, 12, 16));
        }
        if (first == 0x00 && second == 0x64 && (ip[2] & 0xff) == 0xff && (ip[3] & 0xff) == 0x9b) {
            return isPublicIpv4(Arrays.copyOfRange(ip, 12, 16));  // 64:ff9b::/96 (NAT64)
        }
        if (first == 0x20 && second == 0x02) {
            return isPublicIpv4(Arrays.copyOfRange(ip, 2, 6));    // 2002::/16 (6to4)
        }
        return !((first & 0xfe) == 0xfc                           // fc00::/7   privada (ULA)
                || first == 0xff                                  // ff00::/8   multicast
                || (first == 0xfe && (second & 0xc0) == 0x80)     // fe80::/10  enlace local
                || (first == 0xfe && (second & 0xc0) == 0xc0)     // fec0::/10  "site local" (obsoleta)
                || (first == 0x20 && second == 0x01 && ip[2] == 0 && ip[3] == 0)            // 2001::/32 Teredo
                || (first == 0x20 && second == 0x01 && (ip[2] & 0xff) == 0x0d && (ip[3] & 0xff) == 0xb8) // documentación
                || (first == 0x01 && second == 0x00 && allZero(ip, 2, 8))                     // 100::/64 descarte
                || allZero(ip, 0, 16));                           // ::
    }

    private static boolean isIpv4Mapped(byte[] ip) {
        return allZero(ip, 0, 10) && (ip[10] & 0xff) == 0xff && (ip[11] & 0xff) == 0xff;
    }

    private static boolean isIpv4Compatible(byte[] ip) {
        return allZero(ip, 0, 12) && !allZero(ip, 12, 16);
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Atajo para los tests: "10.0.0.1" → false. */
    static boolean isPublic(String literal) throws UnknownHostException {
        return isPublic(InetAddress.getByName(literal));
    }
}
