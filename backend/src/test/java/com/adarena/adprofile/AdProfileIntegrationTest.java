package com.adarena.adprofile;

import com.jayway.jsonpath.JsonPath;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import com.adarena.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdProfileIntegrationTest extends ApiTestSupport {

    // ------------------------------------------------------------------ imágenes

    @Test
    void uploadedImagesAreSanitizedAndServedWithLongCache() throws Exception {
        String token = newUserToken();

        MvcResult upload = uploadImage(token, TestImages.opaquePng(400, 300));
        assertThat(upload.getResponse().getStatus()).isEqualTo(201);
        String url = JsonPath.read(upload.getResponse().getContentAsString(), "$.url");
        assertThat(url).startsWith("/api/public/images/");

        // Pública (sin sesión), re-codificada como JPEG y cacheable un año
        MvcResult image = mockMvc.perform(get(url).with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andReturn();

        // Si el navegador ya la tiene (ETag), respondemos 304 sin volver a enviarla
        String etag = image.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get(url).with(randomIp()).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void filesThatAreNotImagesAreRejected() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "logo.png", "image/png",
                "esto no es una imagen".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/images").file(fake).with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    }

    @Test
    void uploadingRequiresLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", TestImages.opaquePng(100, 100));

        mockMvc.perform(multipart("/api/images").file(file).with(randomIp()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ perfil de anuncio

    @Test
    void profileDoesNotExistUntilCreated() throws Exception {
        mockMvc.perform(get("/api/me/ad-profile").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AD_PROFILE_NOT_FOUND"));
    }

    @Test
    void createAndUpdateProfile() throws Exception {
        String token = newUserToken();
        String imageId = imageId(uploadImage(token, TestImages.transparentPng(300, 300)));

        saveProfile(token, "  Café   Aurora ", "cafeaurora.es", "Café de especialidad en Madrid.", imageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Café Aurora"))       // espacios limpiados
                .andExpect(jsonPath("$.websiteUrl").value("https://cafeaurora.es")) // https añadido
                .andExpect(jsonPath("$.imageUrl").value("/api/public/images/" + imageId));

        saveProfile(token, "Café Aurora Norte", "https://cafeaurora.es/norte", "Ahora también en el norte.", imageId)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/ad-profile").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Café Aurora Norte"))
                .andExpect(jsonPath("$.websiteUrl").value("https://cafeaurora.es/norte"));
    }

    @Test
    void unsafeWebsitesAreRejectedWithAFieldMessage() throws Exception {
        String token = newUserToken();
        String imageId = imageId(uploadImage(token, TestImages.opaquePng(200, 200)));

        for (String url : List.of("http://sin-cifrar.com", "javascript:alert(1)", "https://localhost",
                "https://192.168.1.1", "https://usuario:clave@banco-falso.com")) {
            saveProfile(token, "Empresa", url, "Una descripción válida.", imageId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.websiteUrl").exists());
        }
    }

    @Test
    void cannotUseAnImageUploadedBySomeoneElse() throws Exception {
        String someoneElse = newUserToken();
        String foreignImage = imageId(uploadImage(someoneElse, TestImages.opaquePng(200, 200)));

        saveProfile(newUserToken(), "Empresa", "example.com", "Una descripción válida.", foreignImage)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.imageId").exists());
    }

    @Test
    void requiredFieldsAreValidated() throws Exception {
        mockMvc.perform(put("/api/me/ad-profile").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"\",\"websiteUrl\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName").exists())
                .andExpect(jsonPath("$.errors.websiteUrl").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.imageId").exists());
    }

    @Test
    void descriptionMadeOfBlanksIsTooShort() throws Exception {
        String token = newUserToken();
        String imageId = imageId(uploadImage(token, TestImages.opaquePng(200, 200)));

        saveProfile(token, "Empresa", "example.com", "   hola    ", imageId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    // ------------------------------------------------------------------ helpers

    private String newUserToken() throws Exception {
        return accessToken(register(uniqueEmail(), PASSWORD));
    }

    private MvcResult uploadImage(String token, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "imagen.png", "image/png", bytes);
        return mockMvc.perform(multipart("/api/images").file(file).with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();
    }

    private static String imageId(MvcResult upload) throws Exception {
        return JsonPath.read(upload.getResponse().getContentAsString(), "$.id");
    }

    private ResultActions saveProfile(String token, String company, String url,
                                      String description, String imageId) throws Exception {
        String body = """
                {"companyName":"%s","websiteUrl":"%s","description":"%s","imageId":"%s"}
                """.formatted(company, url, description, imageId);
        return mockMvc.perform(put("/api/me/ad-profile").with(randomIp())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
