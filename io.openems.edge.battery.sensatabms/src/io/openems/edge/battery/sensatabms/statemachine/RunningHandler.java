package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
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
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();


		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during RUNNING, transitioning to ERROR");
			return State.ERROR;
		}
		
		
		// Ensure battery stays started during operation
		if (!battery.isStarted()) {
			battery._setStartStop(StartStop.START); 
		}
			
		// Check if stop is requested
		return switch (battery.getStartStopTarget()) {
			case STOP -> {
				this.log.info("Stop requested, transitioning to GO_STOPPED");
				yield State.GO_STOPPED;
			}
			default ->  {
				this.log.info("Entered Running");
				yield State.RUNNING;
			}
		};
	}
}