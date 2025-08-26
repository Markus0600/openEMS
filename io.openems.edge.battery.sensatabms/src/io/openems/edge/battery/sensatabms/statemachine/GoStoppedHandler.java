package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class GoStoppedHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(GoStoppedHandler.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.log.info("Entering GO_STOPPED state - safely shutting down battery");
		var battery = context.getParent();
		
		// Immediately stop the battery for safety
		battery._setStartStop(StartStop.STOP);
		
		// Request IDLE relay state immediately
		try {
			context.setRequestRelayState(Status.IDLE);
			this.log.info("Requested IDLE relay state for safe shutdown");
		} catch (OpenemsNamedException e) {
			this.log.error("Failed to request IDLE relay state on entry: " + e.getMessage());
		}
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during GO_STOPPED, transitioning to ERROR");
			return State.ERROR;
		}

		// Ensure battery is stopped
		if (battery.isStarted()) {
			battery._setStartStop(StartStop.STOP);
		}

		// Ensure we're requesting IDLE state
		if (context.getRequestRelayState() != Status.IDLE) {
			this.log.info("Ensuring relay sequence IDLE is requested");
			context.setRequestRelayState(Status.IDLE);
		}

		// Check if relay has reached IDLE state - transition complete
		if (context.getRelaySequence() == Status.IDLE.getValue()) {
			this.log.info("Relay reached IDLE state - shutdown complete, transitioning to UNDEFINED");
			return State.UNDEFINED; // Safe resting state
		}

		// Still waiting for relay to reach IDLE
		this.log.debug("Waiting for relay to reach IDLE state (current: {})", context.getRelaySequence());
		return State.GO_STOPPED;
	}

}