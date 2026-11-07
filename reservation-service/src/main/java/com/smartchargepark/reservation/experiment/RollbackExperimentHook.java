package com.smartchargepark.reservation.experiment;

import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Local-only experiment, disabled by default; never controlled by HTTP input. */
@Component
public class RollbackExperimentHook {
    private static final Logger log = LoggerFactory.getLogger(RollbackExperimentHook.class);
    private final RollbackExperimentProperties properties;
    private final Pause pause;

    @Autowired
    public RollbackExperimentHook(RollbackExperimentProperties properties) {
        this(properties, Thread::sleep);
    }

    RollbackExperimentHook(RollbackExperimentProperties properties, Pause pause) {
        this.properties = properties;
        this.pause = pause;
    }

    public void afterOrderCreated(Long reservationId, Long orderId) {
        if (!properties.isEnabled()) {
            return;
        }
        String xid = RootContext.getXID();
        log.info("[SeataRollbackExperiment] stage=after-order-phase-one xid={} reservationId={} orderId={} pauseMs={}",
                xid, reservationId, orderId, properties.getPauseMs());
        try {
            if (properties.getPauseMs() > 0) {
                pause.sleep(properties.getPauseMs());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seata rollback experiment interrupted", interrupted);
        }
        log.info("[SeataRollbackExperiment] stage=throwing xid={} reservationId={} orderId={}",
                xid, reservationId, orderId);
        throw new IllegalStateException("Controlled Seata rollback experiment after order phase one");
    }

    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }
}
