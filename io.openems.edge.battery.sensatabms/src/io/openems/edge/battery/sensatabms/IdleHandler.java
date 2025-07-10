package io.openems.edge.battery.sensatabms;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class IdleHandler extends StateHandler<State, Context> {

	private static final int WAIT_IN_ERROR_STATE_SECONDS = 120;

	private Instant entryAt = Instant.MIN;

	private final Logger log = LoggerFactory.getLogger(Context.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.entryAt = Instant.now();
	}

	@Override
	public State runAndGetNextState(Context context) {

		this.log.info("IdleHandler::runAndGetNextState called.");
		
		// Mark as stopped
		var battery = context.getParent();

//		if (battery.hasFaults()) {
//			return State.IDLE;
//		}

		// Set battery as stopped
		if(battery.isStarted()) {
			battery._setStartStop(StartStop.STOP);
			if(context.getRequestRelayState() != Status.IDLE) {
				try {
					this.log.info("Set relay request: idle (from idle).");
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