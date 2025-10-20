package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.battery.sensatabms.ParallelPack;
import io.openems.edge.battery.sensatabms.SensataBms;

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
		
		int powerSetpoint = ((SensataBms) battery).getLatestEssSetpointW();
		this.log.info("Latest ESS Setpoint: {} W", powerSetpoint);
		
		ParallelPack desired;
		
		if(powerSetpoint < 0) {
			desired = ParallelPack.CHARGE;
			this.log.info("ParallelPack in CHARGE");
		} else {
			desired = ParallelPack.DISCHARGE;
			this.log.info("ParallelPack in DISCHARGE");
		}
		
		if(context.getRequestRelayState() != desired) {
			try {
				context.setRequestRelayState(desired);
				this.log.info("Updated relay state to {} based on Setpoint {}W", desired, powerSetpoint);
			} catch (Exception e) {
				this.log.info("Could not set relay state to {} this cycle. Will retry next cycle", desired);
			}
		}
		
		
		// Ensure battery stays started during operation
		if (!battery.isStarted() && battery.getStartStopTarget() != StartStop.STOP) {
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