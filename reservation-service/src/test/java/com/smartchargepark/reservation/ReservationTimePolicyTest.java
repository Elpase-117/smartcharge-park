package com.smartchargepark.reservation;

import com.smartchargepark.reservation.config.ReservationTimeConfiguration;
import com.smartchargepark.reservation.service.ReservationTimePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTimePolicyTest {
    @Test
    void springProvidesTheShanghaiClockToThePolicy() {
        try (var context = new AnnotationConfigApplicationContext(
                ReservationTimeConfiguration.class, ReservationTimePolicy.class)) {
            assertThat(context.getBean(Clock.class).getZone().getId()).isEqualTo("Asia/Shanghai");
            assertThat(context.getBean(ReservationTimePolicy.class)).isNotNull();
        }
    }

    @Test
    void utcClockIsInterpretedAsShanghaiEvenAcrossTheCalendarBoundary() {
        var policy = ReservationTestTime.at("00:15");
        assertThat(policy.now()).isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 15));
        assertThat(policy.isExpired(ReservationTestTime.TODAY, "09:00-10:00")).isFalse();
        assertThat(policy.isExpired(ReservationTestTime.TODAY.minusDays(1), "19:00-20:00")).isTrue();
    }

    @Test
    void slotStartMustBeStrictlyLaterIncludingSubsecondPrecision() {
        assertThat(ReservationTestTime.at("08:59:59.999999999")
                .isExpired(ReservationTestTime.TODAY, "09:00-10:00")).isFalse();
        assertThat(ReservationTestTime.at("09:00")
                .isExpired(ReservationTestTime.TODAY, "09:00-10:00")).isTrue();
        assertThat(ReservationTestTime.at("09:00:00.000000001")
                .isExpired(ReservationTestTime.TODAY, "09:00-10:00")).isTrue();
    }

    @Test
    void oneImmutableSlotDefinitionControlsValidationAndStartTimeParsing() {
        var policy = ReservationTestTime.at("17:00");
        assertThat(policy.timeSlots()).containsExactly("09:00-10:00", "14:00-15:00", "19:00-20:00");
        assertThat(policy.timeSlots()).allMatch(policy::isSupported);
        assertThat(policy.isSupported(null)).isFalse();
        assertThat(policy.isSupported("10:00-11:00")).isFalse();
        assertThatThrownBy(() -> policy.isExpired(ReservationTestTime.TODAY, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.timeSlots().add("10:00-11:00"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
