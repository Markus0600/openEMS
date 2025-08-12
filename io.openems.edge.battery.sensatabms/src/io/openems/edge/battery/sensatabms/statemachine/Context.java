package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.statemachine.AbstractContext;

public class Context extends AbstractContext<SensataBms> {

	protected final IntegerWriteChannel RequestRelayState;
	protected final IntegerReadChannel RelaySequence;
	protected Status currentRelayState;
	
	private final Logger log = LoggerFactory.getLogger(Context.class);

	public Context(SensataBms parent, IntegerWriteChannel RequestRelayState, IntegerReadChannel RelaySequence) {
		super(parent);
		this.RequestRelayState = RequestRelayState;
		this.RelaySequence = RelaySequence;
		this.currentRelayState = Status.UNDEFINED;
	}
	
	public void setRequestRelayState(Status requestRelayState) throws OpenemsNamedException {
		
		this.log.info("Context::setRequestRelayState trying to set relay to state " + requestRelayState.toString() + ". Current state: " + currentRelayState.toString());
		
		// Null pointer check
		if (this.RequestRelayState == null) {
			this.logInfo(this.log,
					"Request Relay state channel not provided to State Machine context. Cannot switch state.");
			return;
		}
		
		// Range check
		if(
				(requestRelayState != Status.UNDEFINED)
				&& (requestRelayState != Status.IDLE)
				&& (requestRelayState != Status.CHARGE)
				&& (requestRelayState != Status.DISCHARGE)
				&& (requestRelayState != Status.ERROR)
				)
		{
			this.logInfo(this.log,
					"State currently not supported. Cannot switch state.");
			return;			
		}
		
		// Everything fine -> request new relay sequence.
		if(requestRelayState != currentRelayState) {
			this.RequestRelayState.setNextWriteValue(requestRelayState.getValue());
			currentRelayState = requestRelayState;
			this.logInfo(this.log,
					"New state requested: " + requestRelayState.toString());
		}
		this.log.info("Context::setRequestRelayState finished.");

	}
	
    public Status getRequestRelayState() {
        return(this.currentRelayState);
}

    public int getRelaySequence() {
        if (this.RelaySequence == null) {
                return Status.UNDEFINED.getValue();
        }
        var value = this.RelaySequence.value();
        if (value.isDefined()) {
                return value.get();
        }
        return Status.UNDEFINED.getValue();
}

}