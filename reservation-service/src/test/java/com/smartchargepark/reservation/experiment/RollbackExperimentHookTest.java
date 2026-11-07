package com.smartchargepark.reservation.experiment;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class RollbackExperimentHookTest {
    @Test void disabledByDefaultDoesNotPauseOrThrow() {
        var properties = new RollbackExperimentProperties();
        properties.setPauseMs(10000);
        var pauses = new AtomicLong();
        var hook = new RollbackExperimentHook(properties, pauses::addAndGet);
        assertThatCode(() -> hook.afterOrderCreated(6L, 6L)).doesNotThrowAnyException();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(pauses.get()).isZero();
    }

    @Test void enabledPausesThenThrowsUncheckedException() {
        var properties = new RollbackExperimentProperties();
        properties.setEnabled(true);
        properties.setPauseMs(5000);
        var pauses = new AtomicLong();
        var hook = new RollbackExperimentHook(properties, pauses::addAndGet);
        assertThatThrownBy(() -> hook.afterOrderCreated(6L, 6L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("after order phase one");
        assertThat(pauses.get()).isEqualTo(5000);
    }

    @Test void interruptedPauseStillPropagatesFailureAndRestoresInterruptFlag() {
        var properties = new RollbackExperimentProperties();
        properties.setEnabled(true);
        properties.setPauseMs(1000);
        var hook = new RollbackExperimentHook(properties, millis -> { throw new InterruptedException("unit pause"); });
        try {
            assertThatThrownBy(() -> hook.afterOrderCreated(6L, 6L))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
