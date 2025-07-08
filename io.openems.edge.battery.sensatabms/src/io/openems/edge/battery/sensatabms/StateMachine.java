package io.openems.edge.battery.sensatabms;

import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.statemachine.StateHandler;

public class StateMachine extends AbstractStateMachine<StateMachine.State, Context> {

	public enum State implements io.openems.edge.common.statemachine.State<State>, OptionsEnum {
		UNDEFINED(0), 		// Sensata state Undefined
		IDLE(1), 					// Sensata state Idle
		RUNNING(2), 			//	Sensata state charge
		DISCHARGE(3), 		// Sensata state discharge

		ERROR(4), 				// Sensata state error
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
		case IDLE -> new IdleHandler();
		case RUNNING -> new RunningHandler();
		case DISCHARGE -> new DischargeHandler();
		case ERROR -> new ErrorHandler();
		};
	}
}