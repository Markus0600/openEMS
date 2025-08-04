package io.openems.edge.battery.sensatabms.statemachine;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class ErrorHandler extends StateHandler<State, Context> {

    private static final int WAIT_IN_ERROR_STATE_SECONDS = 120;
    private Instant entryAt = Instant.MIN;
    private final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    protected void onEntry(Context context) throws OpenemsNamedException {
        this.entryAt = Instant.now();
        this.log.warn("Entering ERROR state - ensuring battery safety");
        
        var battery = context.getParent();
        
        // Immediate safety: Stop battery and set relay to IDLE
        battery._setStartStop(StartStop.STOP);
        
        try {
            context.setRequestRelayState(Status.IDLE);
            this.log.info("Relay set to IDLE for safety");
        } catch (OpenemsNamedException e) {
            this.log.error("CRITICAL: Failed to set relay to IDLE in ERROR state: " + e.getMessage());
        }
    }

    @Override
    public State runAndGetNextState(Context context) {
        this.log.info("ErrorHandler::runAndGetNextState called.");
        
        var battery = context.getParent();
        
        // Ensure battery stays stopped while in ERROR
        if (battery.isStarted()) {
            battery._setStartStop(StartStop.STOP);
            this.log.warn("Battery was started while in ERROR state - forcing STOP");
        }
        
        // Ensure relay stays in IDLE while in ERROR
        if (context.getRequestRelayState() != Status.IDLE) {
            try {
                context.setRequestRelayState(Status.IDLE);
                this.log.info("Ensuring relay stays IDLE in ERROR state");
            } catch (OpenemsNamedException e) {
                this.log.error("Failed to maintain IDLE relay state: " + e.getMessage());
            }
        }
        
        // Check how long we've been in ERROR state
        var timeInError = Duration.between(this.entryAt, Instant.now()).getSeconds();
        
        // Must wait minimum time before attempting recovery
        if (timeInError < WAIT_IN_ERROR_STATE_SECONDS) {
            this.log.info("Waiting in ERROR state: " + timeInError + "/" + WAIT_IN_ERROR_STATE_SECONDS + " seconds");
            return State.ERROR;
        }
        
        // After wait period, check if faults have cleared
        if (battery.hasFaults()) {
            this.log.warn("Faults still present after " + timeInError + "s - staying in ERROR state");
            // Reset timer to wait another cycle
            this.entryAt = Instant.now();
            return State.ERROR;
        }
        
        // Faults cleared and wait period completed - safe to transition
        this.log.info("Faults cleared after " + timeInError + "s - transitioning to IDLE");
        return State.IDLE;
    }

    @Override
    protected void onExit(Context context) throws OpenemsNamedException {
        this.log.info("Exiting ERROR state - faults have been resolved");
    }
}