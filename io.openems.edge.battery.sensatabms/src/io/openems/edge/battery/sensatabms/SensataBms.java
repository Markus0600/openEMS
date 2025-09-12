package io.openems.edge.battery.sensatabms;

import static io.openems.common.channel.AccessMode.READ_ONLY;
import static io.openems.common.channel.AccessMode.WRITE_ONLY;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
//import io.openems.edge.battery.sensatabms.Status;

public interface SensataBms extends Battery, OpenemsComponent, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		// Channel for contactor control via Modbus / CAN. Possible values according to Sensata documentation:
		// 0: Undefined
		// 1: Idle
		// 2: discharging
		// 3: charging
		// 4: error
		REQUEST_RELAY_STATE(Doc.of(INTEGER) //
				.accessMode(WRITE_ONLY) //
				.text("Set requested contactor sequence. 0=none, 1=idle, 2=discharge, 3=charge, 4=error")),
		RELAY_SEQUENCE(Doc.of(INTEGER) //
				.accessMode(READ_ONLY) //
				.text("Current Relay State. 0=none, 1=idle, 2=discharge, 3=charge, 4=error")),
		RELAY_SEQUENCE_COMPLETED(Doc.of(INTEGER)//
				.accessMode(READ_ONLY)
				.text("0 = Sequence started but not complete\n"
						+ "1 = Last relay request sequence is completed")),
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 * 
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();
	
	/**
	 * Gets the current system status.
	 * 
	 * @return a Status enum containing the current system status
	 */
//	public default Status getSystemBasicStatus() {
//		return this.getSystemBasicStatusChannel().value().asEnum();
//	}

	/**
	 * Get the basic status channel.
	 * 
	 * @return The BASIC_STATUS channel
	 */
//	public default Channel<Status> getSystemBasicStatusChannel() {
//		return this.channel(ChannelId.BASIC_STATUS);
//	}

	/**
	 * Request Relay State channel.
	 * 
	 * @return Request Relay State Channel
	 *
	 */
	public default Channel<Integer> getRelayRequestStateChannel() {
		return this.channel(ChannelId.REQUEST_RELAY_STATE);
	}
	
	/**
	 * Get the Relay Sequence Completed channel.
	 * 
	 * @return Relay Sequence Completed channel
	 * 
	 */
	public default Channel<Integer> getRelaySequenceCompletedChannel(){
		return this.channel(ChannelId.RELAY_SEQUENCE_COMPLETED);
	}
	
	/**
	 * Current ESS setpoint [W] used for Charge/Discharge decision.
	 * Default 0 if not provided by the implementation.
	 */
	public default int getLatestEssSetpointW() {
		return 0;
	}
	
	/**
	 * Deadband [W] around 0 for direction decision. Default 300 W.
	 * 
	 */
	public default int getDeadbandW() {
		return 300;
	}
	
}
