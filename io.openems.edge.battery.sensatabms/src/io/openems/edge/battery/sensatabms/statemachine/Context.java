package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.ParallelPack;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.ShortReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.statemachine.AbstractContext;

public class Context extends AbstractContext<SensataBms> {

	protected final IntegerWriteChannel RequestRelayState;
	protected final ShortReadChannel RelaySequence1;
	protected final ShortReadChannel RelaySequence2;
	protected final ShortReadChannel RelaySequence3;
	protected final ShortReadChannel RelaySequence4;
	protected final ShortReadChannel RelaySequence5;
//	protected final IntegerReadChannel RelaySequenceCompleted; //new for robust statemachine go-running 
	protected ParallelPack currentRelayState;
	protected final ShortReadChannel numPacks;
	
	private final Logger log = LoggerFactory.getLogger(Context.class);

	public Context(SensataBms parent, IntegerWriteChannel RequestRelayState, ShortReadChannel RelaySequence1, ShortReadChannel RelaySequence2, ShortReadChannel RelaySequence3, ShortReadChannel RelaySequence4, ShortReadChannel RelaySequence5, ShortReadChannel NumPacks) {
		super(parent);
		
		this.RequestRelayState = RequestRelayState;
		
		this.RelaySequence1 = RelaySequence1;
		this.RelaySequence2 = RelaySequence2;
		this.RelaySequence3 = RelaySequence3;
		this.RelaySequence4 = RelaySequence4;
		this.RelaySequence5 = RelaySequence5;
		
		this.numPacks = NumPacks;
		
		this.currentRelayState = ParallelPack.IDLE;
	}
	
	public void setRequestRelayState(ParallelPack requestRelayState) throws OpenemsNamedException {
		
		this.log.info("Context::setRequestRelayState trying to set relay to state " + requestRelayState.toString());
		
		// Null pointer check
		if (this.RequestRelayState == null) {
			this.logInfo(this.log, "Request Relay state channel not provided to State Machine context. Cannot switch state.");
			return;
		}
		
		// Range check
		if(requestRelayState != ParallelPack.IDLE
			&& requestRelayState != ParallelPack.CHARGE
			&& requestRelayState != ParallelPack.DISCHARGE){
			this.logInfo(this.log, "State currently not supported. Cannot switch state.");
			return;			
		}
		
		// Everything fine -> request new relay sequence.
		if(requestRelayState != currentRelayState) {
			this.RequestRelayState.setNextWriteValue(requestRelayState.getValue());
			currentRelayState = requestRelayState;
			this.logInfo(this.log, "New state requested: " + requestRelayState.toString());
		}
		
		this.log.info("Context::setRequestRelayState finished.");

	}
	
    public ParallelPack getRequestRelayState() {
    	this.log.debug("Current requested relay state: {}", this.currentRelayState.getName());
        return this.currentRelayState;
    }

    
    public int getRelaySequence1() {
        if (this.RelaySequence1 == null) {
                return ParallelPack.IDLE.getValue();
        }
        var value = this.RelaySequence1.value();
        if (value.isDefined()) {
                return value.get();
        }
        return ParallelPack.IDLE.getValue();
    }
    public int getRelaySequence2() {
        if (this.RelaySequence2 == null) {
                return ParallelPack.IDLE.getValue();
        }
        var value = this.RelaySequence2.value();
        if (value.isDefined()) {
                return value.get();
        }
        return ParallelPack.IDLE.getValue();
    }
    public int getRelaySequence3() {
        if (this.RelaySequence3 == null) {
                return ParallelPack.IDLE.getValue();
        }
        var value = this.RelaySequence3.value();
        if (value.isDefined()) {
                return value.get();
        }
        return ParallelPack.IDLE.getValue();
    }
    public int getRelaySequence4() {
        if (this.RelaySequence4 == null) {
                return ParallelPack.IDLE.getValue();
        }
        var value = this.RelaySequence4.value();
        if (value.isDefined()) {
                return value.get();
        }
        return ParallelPack.IDLE.getValue();
    }
    public int getRelaySequence5() {
        if (this.RelaySequence5 == null) {
                return ParallelPack.IDLE.getValue();
        }
        var value = this.RelaySequence5.value();
        if (value.isDefined()) {
                return value.get();
        }
        return ParallelPack.IDLE.getValue();
    }
   
}