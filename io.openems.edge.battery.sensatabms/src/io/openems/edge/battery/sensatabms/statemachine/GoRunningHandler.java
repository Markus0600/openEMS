package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
//import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.ParallelPack;
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
		
		int relay1 = context.getRelaySequence1();
		int relay2 = context.getRelaySequence2();
		int relay3 = context.getRelaySequence3();
		int relay4 = context.getRelaySequence4();
		int relay5 = context.getRelaySequence5();
		
		if(context.numPacks.value().get() == null) {
			return State.GO_RUNNING;
		}
		
		int iNumPacks = context.numPacks.value().get();
		
		int relay[] = {context.getRelaySequence1(), context.getRelaySequence2(), context.getRelaySequence3(), context.getRelaySequence4(), context.getRelaySequence5()};
		int iNumZeros = 0;
		int iNumOnes = 0;
		int iNumTwos = 0;
		
		for (int i= 0; i<relay.length; i++) {
			if(relay[i] == 0) {
				iNumZeros++;
			}
			if(relay[i] == 1) {
				iNumOnes++;
			}
			if(relay[i] == 2) {
				iNumTwos++;
			}
		}
		
		this.log.info("Current relay sequence from BMS: {},{},{},{},{} | 0: {}, 1: {}, 2: {} | packs: {}", relay1, relay2, relay3, relay4, relay5, iNumZeros, iNumOnes, iNumTwos, iNumPacks);
		
		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during GO_RUNNING, transitioning to ERROR");
			return State.ERROR;
		}
		
		if((iNumTwos >= 1) && (iNumZeros <= (5-iNumPacks))) {
			this.log.info("At least one relay in Sequence {} - transition to RUNNING", iNumTwos);
			context.setRequestRelayState(ParallelPack.DISCHARGE);
	        return State.RUNNING;
		} else if((iNumOnes >= 1) && (iNumZeros <= (5-iNumPacks))) {
			context.setRequestRelayState(ParallelPack.DISCHARGE);
			this.log.info("BMS reports IDLE, requesting DISCHARGE for precharge - transitioning to RUNNING");
		} else {
			context.setRequestRelayState(ParallelPack.IDLE);
			this.log.warn("Relay states unknown - waiting for valid data");
		}
		
//		int maxRelayState = Math.max(relay1, Math.max(relay2, Math.max(relay3, Math.max(relay4, relay5))));
//		int minRelayState = Math.min(relay1, Math.min(relay2, Math.min(relay3, Math.min(relay4, relay5))));
//		
//		if ((maxRelayState == 1) && (minRelayState != 0)) {
//			context.setRequestRelayState(ParallelPack.DISCHARGE);
//			this.log.info("BMS reports IDLE, requesting DISCHARGE for precharge - transitioning to RUNNING");
//		}else if (maxRelayState >= 2 && (minRelayState != 0)) {
//			this.log.info("At least one relay in Sequence {} - transition to RUNNING", maxRelayState);
//	        return State.RUNNING;
//		}else if (relay1 == 0
//					|| relay2 == 0
//					|| relay3 == 0
//					|| relay4 == 0
//					|| relay5 == 0) {
//	        this.log.warn("Relay states unknown - waiting for valid data");
//	    }

		
		return State.GO_RUNNING;
	}
}