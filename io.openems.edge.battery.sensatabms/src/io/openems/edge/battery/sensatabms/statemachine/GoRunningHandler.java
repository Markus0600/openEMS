package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
//import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
//import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class GoRunningHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(GoRunningHandler.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.log.info("Entering GO_RUNNING state - preparing battery for operation");
//		var battery = context.getParent();
		
//		// Ensure battery is set to START when entering GO_RUNNING
//		battery._setStartStop(StartStop.START);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();
		int rs = context.getRelaySequence();
		this.log.info("Current relay sequence from BMS: {}", rs);

		// Check for faults - safety first
		if (battery.hasFaults() || rs == Status.ERROR.getValue()) {
			this.log.warn("Faults detected during GO_RUNNING, transitioning to ERROR");
			return State.ERROR;
		}

		// Wenn BMS IDLE meldet aktiviere Relais
		if (rs == Status.IDLE.getValue()) {
			this.log.info("BMS reports IDLE, requesting PRECHARGE - transitioning to RUNNING");
			return State.RUNNING;
		}
//		if (rs == Status.POWER_ON.getValue() && context.isRelaySequenceCompleted()) {
//		if (rs == Status.POWER_ON.getValue()) {
//			this.log.info("Discharge active and sequence completed, transitioning to RUNNING");
//			return State.RUNNING;
//		}
		
		// Still waiting for BMS to reach IDLE state
		this.log.debug("Waiting for BMS to reach IDLE state (current: {})", rs, context.isRelaySequenceCompleted());
		return State.GO_RUNNING;
	}
}