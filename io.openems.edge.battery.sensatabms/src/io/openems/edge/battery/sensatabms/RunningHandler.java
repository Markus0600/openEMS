package io.openems.edge.battery.sensatabms;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class RunningHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(Context.class);

	@Override
	public State runAndGetNextState(Context context) {

		this.log.info("RunningHandler::runAndGetNextState called.");
		
		var battery = context.getParent();

//		if (battery.hasFaults()) {
//			return State.IDLE;
//		}

		if(!battery.isStarted()) {
			battery._setStartStop(StartStop.START);
			if(context.getRequestRelayState() != Status.RUNNING) {
				try {
					this.log.info("Set relay request: charge / running.");
					context.setRequestRelayState(Status.RUNNING);
				} catch (OpenemsNamedException e) {
					//this.debugLog("StateMachine failed: " + e.getMessage());
					this.log.error("StateMachine failed: " + e.getMessage());
				}
			}
		}
		
		return State.RUNNING;
	}


}