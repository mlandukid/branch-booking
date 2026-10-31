package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ManageTokensTest {

    @Test
    void generatesLongUniqueTokensThatAreSafeInALink() {
        String first = ManageTokens.generate();
        String second = ManageTokens.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashesWithSha256() {
        assertThat(ManageTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void acceptsTheRightTokenOnly() {
        String hash = ManageTokens.hash("the-real-token");

        assertThat(ManageTokens.matches("the-real-token", hash)).isTrue();
        assertThat(ManageTokens.matches("a-guess", hash)).isFalse();
    }

    @Test
    void rejectsMissingValues() {
        assertThat(ManageTokens.matches(null, ManageTokens.hash("x"))).isFalse();
        assertThat(ManageTokens.matches("x", null)).isFalse();
    }
}