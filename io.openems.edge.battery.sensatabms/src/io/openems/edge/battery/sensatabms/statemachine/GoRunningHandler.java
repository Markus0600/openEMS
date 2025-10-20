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
		
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();
		int maxNumPacks = 5;
		
		if(context.numPacks.value().get() == null 
				|| context.numPacks.value().get() == 0 
				|| context.numPacks.value().get() > maxNumPacks) {
			return State.GO_RUNNING;
		}
		
		int iNumPacks = context.numPacks.value().get();
		
		//0=none, 1=inactive, 2=sequence1, (3=sequence2), 4=error
		int relay[] = {context.getRelaySequence1(), context.getRelaySequence2(), context.getRelaySequence3(), context.getRelaySequence4(), context.getRelaySequence5()};
		int countSeqNone = 0;
		int countSeqInactive = 0;
		int countSeq1 = 0;
		int countSeqError = 0; //prepared for Error evaluation
		
		for (int i= 0; i<relay.length; i++) {
			if(relay[i] == 0) {
				countSeqNone++;
			}
			if(relay[i] == 1) {
				countSeqInactive++;
			}
			if(relay[i] == 2) {
				countSeq1++;
			}
			if(relay[i] == 4) {
				countSeqError++;
			}
		}
		
		this.log.info("Current relay sequence from BMS: {},{},{},{},{} | 0: {}, 1: {}, 2: {} | packs: {}", relay[0], relay[1], relay[2], relay[3], relay[4], countSeqNone, countSeqInactive, countSeq1, iNumPacks);
		
		// Check for faults - safety first
		if (battery.hasFaults()) {
			this.log.warn("Faults detected during GO_RUNNING, transitioning to ERROR");
			return State.ERROR;
		}
		
		
		//wenn ein BMS Sequence1 meldet, dann IDLE anfordern
		if(countSeq1 >= 1) {
			this.log.info("At least one relay in Sequence {} - setting relay in IDLE", countSeq1);
			context.setRequestRelayState(ParallelPack.IDLE);	
		//wenn alle verbundenen Racks IDLE melden, dann übergang in RUNNING		
		} else if(countSeqInactive == iNumPacks) {
			this.log.info("all BMS reports IDLE - transitioning to RUNNING");
			return State.RUNNING;
		//wenn ein Rack Error meldet, dann übergang in ERROR
		} else if(countSeqError >= 1) {
			this.log.info("min. 1 BMS reports Error - transitioning to ERROR");
			return State.ERROR;
		} else {
			this.log.warn("Relay states unknown - waiting for valid data");
		}				
	
		return State.GO_RUNNING;
	}
}