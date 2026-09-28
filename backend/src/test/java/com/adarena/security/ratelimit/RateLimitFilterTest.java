package com.adarena.security.ratelimit;

import com.adarena.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En la nube, las llamadas llegan a la API a través de la web (Vercel): el límite por IP debe usar
 * la IP real del visitante que pone la web, pero SOLO si trae la clave compartida correcta.
 */
class RateLimitFilterTest {

    private static final String SECRET = "a-long-shared-secret-1234567890";

    private static RateLimitFilter filter(String secret) {
        return new RateLimitFilter(new AppProperties.RateLimit(true, List.of(), secret), null);
    }

    private static MockHttpServletRequest request(String clientIp, String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/home");
        request.setRemoteAddr("76.76.21.21"); // la IP de Vercel
        if (clientIp != null) request.addHeader(RateLimitFilter.CLIENT_IP_HEADER, clientIp);
        if (secret != null) request.addHeader(RateLimitFilter.PROXY_SECRET_HEADER, secret);
        return request;
    }

    @Test
    void usesTheVisitorsIpWhenTheWebSendsTheRightSecret() {
        assertThat(filter(SECRET).clientIp(request("88.12.34.56", SECRET))).isEqualTo("88.12.34.56");
        assertThat(filter(SECRET).clientIp(request("2a02:9130::1", SECRET))).isEqualTo("2a02:9130::1");
    }

    @Test
    void ignoresTheHeaderWithoutTheSecretOrWithAWrongOne() {
        assertThat(filter(SECRET).clientIp(request("1.2.3.4", null))).isEqualTo("76.76.21.21");
        assertThat(filter(SECRET).clientIp(request("1.2.3.4", "guess"))).isEqualTo("76.76.21.21");
    }

    @Test
    void ignoresTheHeaderWhenNoSecretIsConfigured() {
        assertThat(filter(null).clientIp(request("1.2.3.4", ""))).isEqualTo("76.76.21.21");
        assertThat(filter("short").clientIp(request("1.2.3.4", "short"))).isEqualTo("76.76.21.21");
    }

    @Test
    void rejectsSomethingThatIsNotAnIp() {
        assertThat(filter(SECRET).clientIp(request("1.2.3.4, 5.6.7.8", SECRET))).isEqualTo("76.76.21.21");
        assertThat(filter(SECRET).clientIp(request("<script>", SECRET))).isEqualTo("76.76.21.21");
    }
}
