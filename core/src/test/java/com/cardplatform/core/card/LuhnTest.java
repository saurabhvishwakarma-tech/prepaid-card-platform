package com.cardplatform.core.card;

import com.cardplatform.core.card.service.Luhn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnTest {

    @Test
    void knownTestCardNumbersAreValid() {
        assertThat(Luhn.isValid("4111111111111111")).isTrue();
        assertThat(Luhn.isValid("5555555555554444")).isTrue();
    }

    @Test
    void checkDigitCompletesANumber() {
        assertThat(Luhn.checkDigit("411111111111111")).isEqualTo(1);
    }

    @Test
    void singleMistypedDigitIsCaught() {
        assertThat(Luhn.isValid("4111111111111112")).isFalse();
        assertThat(Luhn.isValid("not-a-card")).isFalse();
    }
}