package com.smartchargepark.reservation.experiment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("smartcharge.experiment.seata-rollback")
public class RollbackExperimentProperties {
    private boolean enabled;
    @Min(0)
    @Max(10000)
    private long pauseMs;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getPauseMs() { return pauseMs; }
    public void setPauseMs(long pauseMs) { this.pauseMs = pauseMs; }
}
