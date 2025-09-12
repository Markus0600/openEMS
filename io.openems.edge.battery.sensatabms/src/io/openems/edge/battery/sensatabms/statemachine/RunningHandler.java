package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class RunningHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(RunningHandler.class);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.log.info("Entering RUNNING state - battery is operational");
		var battery = context.getParent();
		
		// Ensure battery is started when entering RUNNING
		if (!battery.isStarted()) {
			battery._setStartStop(StartStop.START);
		}
	}

	@Override
	public State runAndGetNextState(Context context) {
		var battery = context.getParent();

		// Ensure battery stays started during operation
		if (!battery.isStarted()) {
			battery._setStartStop(StartStop.START);
		}

		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during RUNNING, transitioning to ERROR");
			return State.ERROR;
		}

		// Dynamic relay control based on ESS setpoint
		int p = ((SensataBms) battery).getLatestEssSetpointW();
		this.log.info("Latest Setpoint from ESS {}", p);
//		int db = ((SensataBms) battery).getDeadbandW();
		Status desired = ((p < 0) ? Status.CHARGE : Status.DISCHARGE);
//		Status desired = (Math.abs(p) <= db) ? Status.IDLE : ((p < 0) ? Status.CHARGE : Status.DISCHARGE);

		// Update relay state if needed (IDLE transition handled by GO_STOPPED when STOP requested)
		if (context.getRequestRelayState() != desired) {
			try {
				context.setRequestRelayState(desired);
				this.log.debug("Updated relay state to {} based on setpoint {}W", desired, p);
			} catch (Exception e) {
				this.log.debug("Could not set relay state to {} this cycle. Will retry next cycle.", desired);
			}
		}

		// Check if stop is requested
		return switch (battery.getStartStopTarget()) {
			case STOP -> {
				this.log.info("Stop requested, transitioning to GO_STOPPED");
				yield State.GO_STOPPED;
			}
			default ->  {
				this.log.info("Entered Running");
				this.log.info("Actual Requested Relay State to BMS: {}", desired);
				yield State.RUNNING;
			}
		};
	}
}