package io.openems.edge.battery.sensatabms.statemachine;

import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.statemachine.StateHandler;

public class StateMachine extends AbstractStateMachine<StateMachine.State, Context> {

	public enum State implements io.openems.edge.common.statemachine.State<State>, OptionsEnum {
		UNDEFINED(0), 		// Sensata state Undefined
//		IDLE(1), 					// Sensata state Idle
		RUNNING(2), 			//	Sensata state charge
		ERROR(4), 				// Sensata state error		
		GO_RUNNING(10),		//state for transition idle -> running
		GO_STOPPED(11), 	//state for transition running -> idle 
		;

		private final int value;

		private State(int value) {
			this.value = value;
		}

		@Override
		public int getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.name();
		}

		@Override
		public OptionsEnum getUndefined() {
			return UNDEFINED;
		}

		@Override
		public State[] getStates() {
			return State.values();
		}
	}

	public StateMachine(State initialState) {
		super(initialState);
	}

	@Override
	public StateHandler<State, Context> getStateHandler(State state) {
		return switch (state) {
		case UNDEFINED -> new UndefinedHandler();
//		case IDLE -> new IdleHandler();
		case GO_RUNNING -> new GoRunningHandler();
		case RUNNING -> new RunningHandler();
		case GO_STOPPED -> new GoStoppedHandler();
		case ERROR -> new ErrorHandler();
		};
	}
}