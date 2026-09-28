package com.adarena.site.fetch;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resuelve nombres de dominio dejando SOLO las direcciones públicas. El cliente HTTP se conecta
 * exactamente a las direcciones que devuelve este resolver, así que un DNS que cambie de respuesta
 * entre la comprobación y la conexión ("DNS rebinding") tampoco sirve para colarse en la red interna.
 */
public class PublicDnsResolver implements DnsResolver {

    private final DnsResolver delegate;

    public PublicDnsResolver(DnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] allowed = Arrays.stream(delegate.resolve(host))
                .filter(PublicAddresses::isPublic)
                .toArray(InetAddress[]::new);
        if (allowed.length == 0) {
            throw new UnknownHostException(host + " does not resolve to a public internet address");
        }
        return allowed;
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
        return Arrays.stream(resolve(host)).map(address -> new InetSocketAddress(address, port)).toList();
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }
}
