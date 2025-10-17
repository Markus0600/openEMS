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
		
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();
		
		//0=none, 1=inactive, 2=sequence1, (3=sequence2), 4=error
//		int relay1 = context.getRelaySequence1();
//		int relay2 = context.getRelaySequence2();
//		int relay3 = context.getRelaySequence3();
//		int relay4 = context.getRelaySequence4();
//		int relay5 = context.getRelaySequence5();
		
		if(context.numPacks.value().get() == null) {
			return State.GO_RUNNING;
		}
		
		int iNumPacks = context.numPacks.value().get();
		
		int relay[] = {context.getRelaySequence1(), context.getRelaySequence2(), context.getRelaySequence3(), context.getRelaySequence4(), context.getRelaySequence5()};
		int iNumZeros = 0;
		int iNumOnes = 0;
		int iNumTwos = 0;
		int iNumFours = 0; //prepared for Error evaluation
		
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
			if(relay[i] == 4) {
				iNumFours++;
			}
		}
		
		this.log.info("Current relay sequence from BMS: {},{},{},{},{} | 0: {}, 1: {}, 2: {} | packs: {}", relay[0], relay[1], relay[2], relay[3], relay[4], iNumZeros, iNumOnes, iNumTwos, iNumPacks);
		
		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during GO_RUNNING, transitioning to ERROR");
			return State.ERROR;
		}
		
		
		//wenn eine oder mehr als eine zwei vorhanden UND Anzahl 0 kleiner gleich vorhandene Packs
//		if((iNumTwos >= 1) && (iNumZeros == 0)) {
		if((iNumTwos >= 1) && (iNumZeros <= (relay.length - iNumPacks))) {
			this.log.info("At least one relay in Sequence {} - setting relay in IDLE", iNumTwos);
			context.setRequestRelayState(ParallelPack.IDLE);
			
		//wenn mind. 1 in IDLE UND Anzahl Null kleiner gleich restlichen verbunden Racks		
		} else if((iNumOnes >= 1) && (iNumZeros <= (relay.length - iNumPacks))) {
			this.log.info("BMS reports IDLE - transitioning to RUNNING");
			return State.RUNNING;
		} else {
			this.log.warn("Relay states unknown - waiting for valid data");
		}				
	
		return State.GO_RUNNING;
	}
}