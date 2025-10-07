package io.openems.edge.battery.sensatabms;

import static io.openems.common.channel.AccessMode.READ_ONLY;
import static io.openems.common.channel.AccessMode.WRITE_ONLY;
import static io.openems.common.types.OpenemsType.SHORT;
import static io.openems.common.types.OpenemsType.INTEGER;
import static io.openems.common.types.OpenemsType.FLOAT;
import static io.openems.common.types.OpenemsType.DOUBLE;

//import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
//import io.openems.edge.battery.sensatabms.Status;

public interface SensataBms extends Battery, OpenemsComponent, StartStoppable {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		// Channels already available via Battery class:
		// SOC	 -> Sensata ID: 48539
		// SOH -> Sensata ID: 48540
		// CURRENT
		// CAPACITY -> Sensata ID: 48542
		// CHARGE_MAX_VOLTAGE
		// CHARGE_MAX_CURRENT -> Sensata ID: 48548
		// DISCHARGE_MIN_VOLTAGE
		// DISCHARGE_MAX_CURRENT -> Sensata ID: 48549
		// MIN_CELL_TEMPERATURE -> Sensata ID: 48552
		// MAX_CELL_TEMPERATURE -> Sensata ID: 48553
		// MIN_CELL_VOLTAGE -> Sensata ID: 48554
		// MAX_CELL_VOLTAGE -> Sensata ID: 48555
		// INNER_RESISTANCE
		
		// Cheat sheet: available OpenEMS types:
		// BOOLEAN, SHORT, INTEGER, LONG,  FLOAT, DOUBLE, STRING
		
		// Sensata ID: 48320
		// Name: PARALLEL_PACKS_STATE
		// Modbus register: 300
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_STATE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("State of the parallel pack system: UNDEFINED = 0, DISABLED = 1, INITIALIZING = 2, RUNNING = 3, WARNING = 4, ERROR = 5,\n"
						+ "ERROR_PACK_COUNT = 6\n"
						+ "ERROR_PACKS_CALIBRATION_CRC_MISMATCH = 7 ERROR_CROSS_PACK_COMM_INITIALIZATION_FAILED = 8, VERIFYING = 9")),
		
		// Sensata ID: 48327
		// Name: PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS
		// Modbus register: 301
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS(Doc.of(INTEGER) //
				.accessMode(READ_ONLY) //
				.text("Number of currently detected packs")),
		
		// Sensata ID: 48538
		// Name: PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE
		// Modbus register: 310
		// Type: int16
		// Unit: %
		PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE(Doc.of(INTEGER) //
				.accessMode(READ_ONLY) //
				.text("Aggregated SoC based on SoCs from all packs currently connected to the high voltage bus")),
		
		// ### already available in Battery channel -> SOC
		// Sensata ID: 48539
		// Name: PARALLEL_PACKS_AGGREGATED_SOC_TOTAL
		// Modbus register: 311
		// Type: int16
		// Unit: %
		
		// ### already available in Battery channel -> SOH
		// Sensata ID: 48540
		// Name: PARALLEL_PACKS_AGGREGATED_SOH_TOTAL
		// Modbus register: 320
		// Type: float32
		// Unit: %

		// Sensata ID: 48541
		// Name: PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE
		// Modbus register: 330
		// Type: unit32
		// Unit: As
		PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE(Doc.of(INTEGER) //
				.accessMode(READ_ONLY) //
				.text("Aggregated charge capacity of all packs detected in the system")),

		// ### already available in Battery channel -> CAPACITY
		// Sensata ID: 48542
		// Name: PARALLEL_PACKS_AGGREGATED_CAPACITY_TOTAL
		// Modbus register: 332
		// Type: unit32
		// Unit: As

		// Sensata ID: 48543
		// Name: PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT
		// Modbus register: 340
		// Type: float64
		// Unit: A
		PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT(Doc.of(DOUBLE) //
				.accessMode(READ_ONLY) //
				.text("Aggregated requested charge current of all packs currently connected to the high voltage bus")),

		// Sensata ID: 48544
		// Name: PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE
		// Modbus register: 350
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Number of online packs with active contactor charge sequence")),

		// Sensata ID: 48545
		// Name: PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE
		// Modbus register: 351
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Number of online packs with active contactor discharge sequence")),

		// Sensata ID: 48546
		// Name: PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE
		// Modbus register: 352
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Number of online packs that have identified a missed discharge issue")),

		// Sensata ID: 48547
		// Name: PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE
		// Modbus register: 353
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Number of online packs that have identified a missed charge issue")),

		// ### already available in Battery channel -> CHARGE_MAX_CURRENT
		// Sensata ID: 48548
		// Name: PARALLEL_PACKS_AGGREGATED_DCLI
		// Modbus register: 360
		// Type: float64
		// Unit: A

		// ### already available in Battery channel -> DISCHARGE_MAX_CURRENT
		// Sensata ID: 48549
		// Name: PARALLEL_PACKS_AGGREGATED_DCLO
		// Modbus register: 370
		// Type: float64
		// Unit: A

		// Sensata ID: 48550
		// Name: PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS
		// Modbus register: 380
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Aggregated balancing status: 0 = Balancing off, 1 = some packs bleeding, 2 = all packs completed balancing")),

		// Sensata ID: 48551
		// Name: PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS
		// Modbus register: 381
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Aggregated weld status: 0 = no welded contactors reported, 1 = at least one pack reporting welded main-contactor(s)")),

		// ### already available in Battery channel -> MIN_CELL_TEMPERATURE
		// Sensata ID: 48552
		// Name: PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE
		// Modbus register: 390
		// Type: uint16
		// Unit: 0.1°C

		// ### already available in Battery channel -> MAX_CELL_TEMPERATURE
		// Sensata ID: 48553
		// Name: PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE
		// Modbus register: 391
		// Type: uint16
		// Unit: 0.1°C

		// ### already available in Battery channel -> MIN_CELL_VOLTAGE
		// Sensata ID: 48554
		// Name: PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE
		// Modbus register: 400
		// Type: uint16
		// Unit: mV

		// ### already available in Battery channel -> MAX_CELL_VOLTAGE
		// Sensata ID: 48555
		// Name: PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE
		// Modbus register: 401
		// Type: uint16
		// Unit: mV

		// Sensata ID: 48556
		// Name: PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS
		// Modbus register: 410
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Aggregated charge complete status: 1 = all parallel packs fully charged, 0 = not all parallel packs fully charged")),

		// Sensata ID: 48557
		// Name: PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE
		// Modbus register: 411
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Aggregated system state: highest system state reported by any parallel pack")),

		// Sensata ID: 48558
		// Name: PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS
		// Modbus register: 412
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Number of packs connected (responding to CAN communication)")),

		// Sensata ID: 48560
		// Name: PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX
		// Modbus register: 413
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Index of parallel pack reporting min cell temperature among all connected packs")),

		// Sensata ID: 48561
		// Name: PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX
		// Modbus register: 414
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Index of parallel pack reporting max cell temperature among all connected packs")),

		// Sensata ID: 48562
		// Name: PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX
		// Modbus register: 415
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Index of parallel pack reporting min cell voltage among all connected packs")),

		// Sensata ID: 48563
		// Name: PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX
		// Modbus register: 416
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Index of parallel pack reporting max cell voltage among all connected packs")),

		// Sensata ID: 48418...48427
		// Name: PARALLEL_PACKS_PPAIDx_RELAY_SEQUENCE
		// Modbus register: 420..24, 430..34
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 1")),
		PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 2")),
		PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 3")),
		PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 4")),
		PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 5")),
		PARALLEL_PACKS_PPAID6_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 6")),
		PARALLEL_PACKS_PPAID7_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 7")),
		PARALLEL_PACKS_PPAID8_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 8")),
		PARALLEL_PACKS_PPAID9_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 9")),
		PARALLEL_PACKS_PPAID10_RELAY_SEQUENCE(Doc.of(SHORT) //
				.accessMode(READ_ONLY) //
				.text("Active relay sequence reported by pack with parallel Pack + ID 10")),
		
		// Sensata ID: 48604
		// Name: PARALLEL_PACKS_REQUEST_RELAY_STATE
		// Modbus register: 300 (write)
		// Type: uint8
		// Unit: -
		PARALLEL_PACKS_REQUEST_RELAY_STATE(Doc.of(SHORT) //
				.accessMode(WRITE_ONLY) //
				.text("IDLE = 0, CHARGE = 1, DISCHARGE = 2")),

		// Sensata ID: 45016
		// Name: KEEP_ALIVE
		// Modbus register: 301 (write)
		// Type: uint8
		// Unit: -
		HEART_BEAT(Doc.of(SHORT) //
				.accessMode(WRITE_ONLY) //
				.text("Heart beat for BMS")),

//		// Channel for contactor control via Modbus / CAN. Possible values according to Sensata documentation:
//		// 0: Undefined
//		// 1: Idle
//		// 2: charging
//		// 3: discharging
//		// 4: error
//		REQUEST_RELAY_STATE(Doc.of(INTEGER) //
//				.accessMode(WRITE_ONLY) //
//				.text("Set requested contactor sequence. 0=none, 1=idle, 2=charge, 3=discharge, 4=error")),
//		RELAY_SEQUENCE(Doc.of(INTEGER) //
//				.accessMode(READ_ONLY) //
//				.text("Current Relay State. 0=none, 1=idle, 2=charge, 3=discharge, 4=error")),		
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
		return this.channel(ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE);
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
	
	/**
	 * Get the Channel for Inhibit Balancing. Return false if not provided by the implementation
	 * 
	 */
	public default boolean getEnableBalancing() {
		return false;
	}
	
}
