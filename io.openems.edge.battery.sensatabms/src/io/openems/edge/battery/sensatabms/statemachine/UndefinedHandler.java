package io.openems.edge.battery.sensatabms.statemachine;

//import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.ParallelPack;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class UndefinedHandler extends StateHandler<State, Context> {

//	private Instant entryAt = Instant.MIN;

	private final Logger log = LoggerFactory.getLogger(UndefinedHandler.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
//		this.entryAt = Instant.now();
		this.log.info("Entering UNDEFINED state - battery in safe resting state");
		
		var battery = context.getParent();
		
		// Ensure battery is stopped and relay is IDLE for safety
		battery._setStartStop(StartStop.STOP);
		
		try {
			context.setRequestRelayState(ParallelPack.IDLE);
			this.log.info("Set relay to IDLE for safety");
		} catch (OpenemsNamedException e) {
			this.log.error("Failed to set relay to IDLE in UNDEFINED state: " + e.getMessage());
		}
	}

	@Override
	public State runAndGetNextState(Context context) {

		this.log.info("UndefinedHandler::runAndGetNextState called.");

		var battery = context.getParent();

		// Check for faults first - highest priority
		if (battery.hasFaults()) {
		    this.log.info("Fault detected, going to ERROR");
		    return State.ERROR;
		}

		// Check if we should start the battery
		if (battery.getStartStopTarget() == StartStop.START) {
			this.log.info("Start requested, transitioning to GO_RUNNING");
			return State.GO_RUNNING;
		}

		// Default behavior: ensure battery is stopped and relay is IDLE
		if (battery.isStarted()) {
			battery._setStartStop(StartStop.STOP);
		}
		
		if (context.getRequestRelayState() != ParallelPack.IDLE) {
			try {
				this.log.info("Set relay request: IDLE (from UNDEFINED).");
				context.setRequestRelayState(ParallelPack.IDLE);
			} catch (OpenemsNamedException e) {
				this.log.error("Failed to set relay to IDLE in UNDEFINED state: " + e.getMessage());
			}
		}

		// Stay in UNDEFINED until start is requested
		return State.UNDEFINED;
	}

}