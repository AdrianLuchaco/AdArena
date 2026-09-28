package com.adarena.site.extract;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Los vídeos de YouTube se ven dentro de AdArena con su reproductor oficial; los canales, no. */
class EmbedLinksTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ"
    })
    void videosUseThePrivacyFriendlyPlayer(String url) {
        assertThat(EmbedLinks.embedUrl(url))
                .contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/@micanal",
            "https://www.youtube.com/channel/UC123",
            "https://www.youtube.com/watch?v=corto",
            "https://www.instagram.com/martacocina/",
            "https://www.cafe-aurora.es/"
    })
    void anythingElseHasNoEmbed(String url) {
        assertThat(EmbedLinks.embedUrl(url)).isEmpty();
    }
}
