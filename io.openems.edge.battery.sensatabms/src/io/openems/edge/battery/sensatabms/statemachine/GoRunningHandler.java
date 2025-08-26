package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class GoRunningHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(GoRunningHandler.class);

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		// Bei GO_RUNNING aktiv starten
		battery._setStartStop(StartStop.START);

		if (battery.hasFaults()) {
			return State.ERROR;
		}

		// Wenn BMS IDLE meldet aktiviere Discharge für Precharge -> RUNNING
		int rs = context.getRelaySequence();
		this.log.info("actual relay state BMS: {}" + rs);
		
		if (rs == Status.IDLE.getValue()) {
			context.setRequestRelayState(Status.DISCHARGE);
			this.log.info("activated Precharge -> State Running");
			return State.RUNNING;
		}
		
		return State.GO_RUNNING;
	}
}