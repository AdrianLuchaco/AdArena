package com.adarena.site.fetch;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Protección SSRF: al leer la web de un proyecto, AdArena solo puede conectarse a direcciones
 * públicas de internet, nunca a su propia red.
 */
class PublicAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.8.9.10", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.10",
            "169.254.169.254",          // metadatos del servidor en la nube
            "100.64.0.1", "100.127.255.254", "192.0.2.1", "198.18.0.1", "198.51.100.7", "203.0.113.9",
            "224.0.0.1", "240.0.0.1", "255.255.255.255",
            "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "fec0::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1",       // IPv4 escondida en IPv6
            "64:ff9b::a00:1",                             // NAT64 de 10.0.0.1
            "2002:0a00:0001::1",                          // 6to4 de 10.0.0.1
            "2001:db8::1", "2001:0:4136:e378::1"          // documentación y Teredo
    })
    void internalAddressesAreNeverPublic(String address) throws UnknownHostException {
        assertThat(PublicAddresses.isPublic(address)).as(address).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.215.14", "172.32.0.1", "100.128.0.1",
            "2606:4700:4700::1111", "2a00:1450:4003:80e::200e"})
    void internetAddressesArePublic(String address) throws UnknownHostException {
        assertThat(PublicAddresses.isPublic(address)).as(address).isTrue();
    }

    @Test
    void theResolverDropsInternalAddressesAndFailsIfNothingIsLeft() throws Exception {
        DnsResolver mixed = fakeDns("93.184.215.14", "10.0.0.5");
        assertThat(new PublicDnsResolver(mixed).resolve("mixed.example"))
                .extracting(InetAddress::getHostAddress)
                .containsExactly("93.184.215.14");

        // Un dominio que "apunta" a la red interna (o al servidor de metadatos) no se puede leer
        PublicDnsResolver internal = new PublicDnsResolver(fakeDns("169.254.169.254", "127.0.0.1"));
        assertThatThrownBy(() -> internal.resolve("evil.example")).isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> internal.resolve("evil.example", 443)).isInstanceOf(UnknownHostException.class);
    }

    private static DnsResolver fakeDns(String... addresses) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] result = new InetAddress[addresses.length];
                for (int i = 0; i < addresses.length; i++) {
                    result[i] = InetAddress.getByName(addresses[i]);
                }
                return result;
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }
}
