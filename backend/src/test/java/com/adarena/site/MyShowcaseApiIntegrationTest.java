package com.adarena.site;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.image.service.ImageService;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import com.adarena.support.TestImages;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Así se verá tu anuncio si ganas": la presentación montada con la web del anunciante. */
@IntegrationTest
class MyShowcaseApiIntegrationTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired SitePreviewRepository sitePreviewRepository;
    @Autowired SitePreviewJson json;

    String token;
    UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        String email = uniqueEmail();
        token = accessToken(register(email, PASSWORD));
        userId = userRepository.findByEmail(User.normalizeEmail(email)).orElseThrow().getId();
    }

    private String createProfile() {
        String url = "https://www.estudio-" + UUID.randomUUID() + ".es/";
        UUID image = imageService.store(userId, TestImages.opaquePng(200, 200)).getId();
        adProfileRepository.save(new AdProfile(userId, "Estudio Norte", url, "Fotografía para marcas pequeñas.", image));
        return url;
    }

    @Test
    void withoutAnAdThereIsNothingToShow() throws Exception {
        mockMvc.perform(get("/api/me/ad-profile/showcase").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void showsThePresentationAndLimitsHowOftenItCanBeReadAgain() throws Exception {
        String url = createProfile();
        SitePreview preview = new SitePreview(url, userId, SitePreview.Source.WEB);
        preview.update(new SitePreview.Content(url, false, null, "Estudio Norte", "Fotos que venden",
                "Fotografía de producto.", "#312e81", null, null, null, null, "[]",
                json.writeHighlights(List.of("Sesiones a domicilio", "Entrega en 72 horas"))), Instant.now());
        sitePreviewRepository.save(preview);

        mockMvc.perform(get("/api/me/ad-profile/showcase").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.showcase.title").value("Fotos que venden"))
                .andExpect(jsonPath("$.showcase.highlights[1]").value("Entrega en 72 horas"))
                .andExpect(jsonPath("$.websiteUrl").value(url))
                .andExpect(jsonPath("$.canRefreshAt").isNotEmpty());

        // Acabamos de leerla: hay que esperar 2 minutos para pedirlo otra vez
        mockMvc.perform(post("/api/me/ad-profile/showcase/refresh").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWCASE_REFRESH_TOO_SOON"));
    }

    @Test
    void onlyTheAdminCanReReadAWinnersWebsite() throws Exception {
        mockMvc.perform(post("/api/admin/ad-slots/" + UUID.randomUUID() + "/showcase/refresh").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }
}
