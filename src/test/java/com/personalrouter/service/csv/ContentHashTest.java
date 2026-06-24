package com.personalrouter.service.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    void sha256HexMatchesKnownVector() {
        assertThat(ContentHash.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sameBytesProduceSameHash() {
        byte[] a = "concessionaria;praca".getBytes(StandardCharsets.UTF_8);
        byte[] b = "concessionaria;praca".getBytes(StandardCharsets.UTF_8);
        assertThat(ContentHash.sha256Hex(a)).isEqualTo(ContentHash.sha256Hex(b));
    }
}
