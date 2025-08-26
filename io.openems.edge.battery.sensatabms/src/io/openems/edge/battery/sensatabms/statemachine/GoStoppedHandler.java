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
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		// In GO_STOPPED: sicher anhalten
		battery._setStartStop(StartStop.STOP);

		if (battery.hasFaults()) {
			return State.ERROR;
		}

		// Ziel: IDLE erreichen
		if (context.getRequestRelayState() != Status.IDLE) {
			this.log.info("Request relay sequence IDLE");
			context.setRequestRelayState(Status.IDLE);
		}

		// Übergang abgeschlossen, wenn Relais IDLE meldet
		if (context.getRelaySequence() == Status.IDLE.getValue()) {
			return State.UNDEFINED; // sicherer Ruhezustand
		}

		return State.GO_STOPPED;
	}

}