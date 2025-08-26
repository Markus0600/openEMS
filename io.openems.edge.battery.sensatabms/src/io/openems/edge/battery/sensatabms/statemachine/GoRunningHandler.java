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
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.log.info("Entering GO_RUNNING state - preparing battery for operation");
		var battery = context.getParent();
		
		// Ensure battery is set to START when entering GO_RUNNING
		battery._setStartStop(StartStop.START);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during GO_RUNNING, transitioning to ERROR");
			return State.ERROR;
		}

		// Ensure battery is actively starting
		if (!battery.isStarted()) {
			battery._setStartStop(StartStop.START);
		}

		// Wenn BMS IDLE meldet aktiviere Discharge für Precharge -> RUNNING
		int rs = context.getRelaySequence();
		this.log.info("Current relay sequence from BMS: {}", rs);
		
		if (rs == Status.IDLE.getValue()) {
			context.setRequestRelayState(Status.DISCHARGE);
			this.log.info("BMS reports IDLE, requesting DISCHARGE for precharge - transitioning to RUNNING");
			return State.RUNNING;
		}
		
		// Still waiting for BMS to reach IDLE state
		this.log.debug("Waiting for BMS to reach IDLE state (current: {})", rs);
		return State.GO_RUNNING;
	}
}