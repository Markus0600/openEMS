package io.openems.edge.battery.sensatabms.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class RunningHandler extends StateHandler<State, Context> {

	private final Logger log = LoggerFactory.getLogger(RunningHandler.class);

	@Override
	public State runAndGetNextState(Context context) {
		var battery = context.getParent();

		if (!battery.isStarted()) {
			battery._setStartStop(StartStop.START);
		}

		if (battery.hasFaults()) {
			return State.ERROR;
		}

		// Richtung regelmäßig nachführen
		int p = ((SensataBms) battery).getLatestEssSetpointW();
		int db = ((SensataBms) battery).getDeadbandW();
		Status desired = (Math.abs(p) <= db) ? Status.IDLE : ((p < 0) ? Status.CHARGE : Status.DISCHARGE);

		// Wenn gewünschte Richtung != IDLE, dann anfordern (IDLE-Übergang übernimmt GO_STOPPED bei STOP)
		if (context.getRequestRelayState() != desired) {
			try {
				context.setRequestRelayState(desired);
			} catch (Exception e) {
				this.log.debug("Could not set relay state to {} this cycle. Will retry.", desired);
			}
		}

		return switch (battery.getStartStopTarget()) {
			case STOP -> State.GO_STOPPED;
			default -> State.RUNNING;
		};
	}
}