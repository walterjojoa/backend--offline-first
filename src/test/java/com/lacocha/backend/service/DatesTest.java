package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class DatesTest {

    @Test
    void normalDateIsValid() {
        assertThat(Dates.deviceDateError(OffsetDateTime.now(ZoneOffset.ofHours(-5)).minusHours(3))).isNull();
    }

    @Test
    void aFewHoursAheadAreTolerated() {
        assertThat(Dates.deviceDateError(OffsetDateTime.now().plusHours(6))).isNull();
    }

    @Test
    void moreThanOneDayAheadIsRejected() {
        assertThat(Dates.deviceDateError(OffsetDateTime.now().plusDays(2))).contains("futuro");
    }

    @Test
    void clockIn1970IsRejected() {
        assertThat(Dates.deviceDateError(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
                .contains("antigua");
    }

    @Test
    void noDateNoError() {
        assertThat(Dates.deviceDateError(null)).isNull();
    }
}
