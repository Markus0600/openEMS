package io.openems.edge.battery.sensatabms.statemachine;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class UndefinedHandler extends StateHandler<State, Context> {

	private static final int WAIT_IN_ERROR_STATE_SECONDS = 120;

	private Instant entryAt = Instant.MIN;

	private final Logger log = LoggerFactory.getLogger(Context.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.entryAt = Instant.now();
	}

	@Override
	public State runAndGetNextState(Context context) {

		this.log.info("UndefinedHandler::runAndGetNextState called.");

		// Mark as stopped
		// Currently, on error switch to state idle
		// TODO: maybe change later on.
		var battery = context.getParent();

		if (battery.hasFaults()) {
		    this.log.info("Fault detected, going to ERROR");
		    return State.ERROR;
		}

		// Set battery as stopped
		if(battery.isStarted()) {
			battery._setStartStop(StartStop.STOP);
			if(context.getRequestRelayState() != Status.IDLE) {
				try {
					this.log.info("Set relay request: idle (from undefined).");
					context.setRequestRelayState(Status.IDLE);
				} catch (OpenemsNamedException e) {
					//this.debugLog("StateMachine failed: " + e.getMessage());
					this.log.error("StateMachine failed: " + e.getMessage());
				}
			}
		}

		return State.IDLE;
	}

}