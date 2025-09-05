package io.openems.edge.battery.sensatabms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;

import java.util.concurrent.atomic.AtomicReference;

																						 

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

															
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
																	   
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.sensatabms.statemachine.Context;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.FloatQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;																		   
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;																		   
																		
																 
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.ComponentManager;
														  
														 
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
												   
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
										   


@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "SensataBMS", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
})
public class SensataBmsImpl extends AbstractOpenemsModbusComponent
		implements SensataBms, Battery, ModbusComponent, OpenemsComponent, EventHandler, StartStoppable {

	private Config config = null;
	private final Logger log = LoggerFactory.getLogger(SensataBmsImpl.class);

	// ESS setpoint tracking
	private volatile int latestEssSetpoint = 0;
	private static final int DEADBAND = 300;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	/**
	 * Manages the {@link State}s of the StateMachine.
	 */
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public SensataBmsImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				SensataBms.ChannelId.values(), //
				Battery.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.log.info("SensataBmsImpl::activate called.");
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id())) {
			return;
		}
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.log.info("SensataBmsImpl::deactivate called.");

		super.deactivate();

		// switch off relay if shutdown
		try {
			IntegerWriteChannel requestRelayState = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE);
			requestRelayState.setNextWriteValue(Status.IDLE.getValue());
			this.log.info("Relay set to IDLE during deactivation.");
		} catch (Exception e) {
			this.log.error("Failed to set relay to IDLE on deactivate: " + e.getMessage(), e);

		}
	}

	// Modbus addresses used for communication with Sensata BMS
//	private static final int CAPACITY = 100; // remaining capacity, Sensata ID 45000
//	private static final int CHARGE_MAX_CURRENT = 110; // max. charge current, Sensata ID 45004
//	private static final int DISCHARGE_MAX_CURRENT = 120; // max. discharge current, Sensata ID 45005
//	private static final int SOC = 140; // state of charge, Sensata ID 45015
//	private static final int SOH = 160; // state of health, Sensata ID 45045
//	private static final int CURRENT = 170; // pack current, Sensata ID 547
//	private static final int MIN_CELL_VOLTAGE = 180; // min cell voltage, Sensata ID 10405
//	private static final int MAX_CELL_VOLTAGE = 190; // max cell voltage, Sensata ID 10406
//	private static final int VOLTAGE = 200; // cmu - sum of cell voltage, Sensata ID 11400
//	private static final int LOGGED_CELL_TEMP_MIN = 210;
//	private static final int LOGGED_CELL_TEMP_MAX = 220;
//	private static final int RELAY_SEQUENCE = 230;
	private static final int PARALLEL_PACKS_STATE = 300;
	private static final int PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS = 301;
	private static final int PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE = 310;
	private static final int PARALLEL_PACKS_AGGREGATED_SOC_TOTAL = 311;
	private static final int PARALLEL_PACKS_AGGREGATED_SOH_TOTAL = 320;
	private static final int PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE = 330;
	private static final int PARALLEL_PACKS_AGGREGATED_CAPACITY_TOTAL = 332;
	private static final int PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT = 340;
	private static final int PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE = 350;
	private static final int PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE = 351;
	private static final int PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE = 352;
	private static final int PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE = 353;
	private static final int PARALLEL_PACKS_AGGREGATED_DCLI = 360;
	private static final int PARALLEL_PACKS_AGGREGATED_DCLO = 370;
	private static final int PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS = 380;
	private static final int PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS = 381;
	private static final int PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE = 390;
	private static final int PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE = 391;
	private static final int PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE = 400;
	private static final int PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE = 401;
	private static final int PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS = 410;
	private static final int PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE = 411;
	private static final int PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS = 412;
	private static final int PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX = 413;
	private static final int PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX = 414;
	private static final int PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX = 415;
	private static final int PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX = 416;
	private static final int PARALLEL_PACKS_REQUEST_RELAY_STATE = 300;
	
	// Modbus addresses used for communication with Sensata BMS - write only
	private static final int REQUEST_RELAY_STATE = 100;

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		this.log.info("SensataBmsImpl::defineModbusProtocol called.");

								  
		return new ModbusProtocol(this,

				// Values required for the battery channel - read only
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_STATE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_STATE, new UnsignedWordElement(PARALLEL_PACKS_STATE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS, new UnsignedWordElement(PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE, new SignedWordElement(PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE), SCALE_FACTOR_MINUS_2) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SOC_TOTAL, //
						Priority.LOW, //
						m(Battery.ChannelId.SOC, new SignedWordElement(PARALLEL_PACKS_AGGREGATED_SOC_TOTAL), SCALE_FACTOR_MINUS_2) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SOH_TOTAL, //
						Priority.LOW, //
						m(Battery.ChannelId.SOH, new FloatDoublewordElement(PARALLEL_PACKS_AGGREGATED_SOH_TOTAL)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE, new SignedDoublewordElement(PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CAPACITY_TOTAL, //
						Priority.LOW, //
						m(Battery.ChannelId.CAPACITY, new SignedDoublewordElement(PARALLEL_PACKS_AGGREGATED_CAPACITY_TOTAL)) //
				), //
				// TODO: Equivalent für den Parallel-Pack Mode? Ggf. PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT?
//				new FC3ReadRegistersTask(//
//						CURRENT, //
//						Priority.LOW, //
//						m(Battery.ChannelId.CURRENT, new FloatQuadruplewordElement(CURRENT)) //
//				), //
				// TODO: ist das der CURRENT aus der Battery-Klasse?
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_DCLI, //
						Priority.LOW, //
						m(Battery.ChannelId.CHARGE_MAX_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_AGGREGATED_DCLI)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_DCLO, //
						Priority.LOW, //
						m(Battery.ChannelId.DISCHARGE_MAX_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_AGGREGATED_DCLO)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS)) //
				), //
				// TODO: Equivalent für den Parallel-Pack Mode?
//				new FC3ReadRegistersTask(//
//						VOLTAGE, //
//						Priority.LOW, //
//						m(Battery.ChannelId.VOLTAGE, new FloatQuadruplewordElement(VOLTAGE)) //
//				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE, //
						Priority.LOW, //
						m(Battery.ChannelId.MIN_CELL_TEMPERATURE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE, //
						Priority.LOW, //
						m(Battery.ChannelId.MAX_CELL_TEMPERATURE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE, //
						Priority.LOW, //
						m(Battery.ChannelId.MIN_CELL_VOLTAGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE), SCALE_FACTOR_3) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE, //
						Priority.LOW, //
						m(Battery.ChannelId.MAX_CELL_VOLTAGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE), SCALE_FACTOR_3) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX)) //
				), //
				// Values required for Sensata itself - write only
				new FC6WriteRegisterTask(//
						PARALLEL_PACKS_REQUEST_RELAY_STATE, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE, new UnsignedWordElement(PARALLEL_PACKS_REQUEST_RELAY_STATE)) //
				) //
			 
		);
	}

	@Override
	public void handleEvent(Event event) {
																																
		if (!this.isEnabled()) {
			return;
		}
							 
		if (EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE.equals(event.getTopic())) {
			// Setpoint pro Zyklus aktualisieren
			this.updateLatestEssSetpointFromEssChannel();
			// State-Machine ausführen
			this.handleStateMachine();
		}
   
	}

	/**
	 * Reads the final ESS setpoint via literal channel "DebugSetActivePower" from the
	 * component configured via ess_id. If unavailable, defaults to 0.
	 */
	private void updateLatestEssSetpointFromEssChannel() {
		try {
			final String essId = (this.config != null) ? this.config.ess_id() : null;
			if (essId == null || essId.isBlank()) {
				this.latestEssSetpoint = 0;
				return;
			}

			final ChannelAddress addr = new ChannelAddress(essId, "DebugSetActivePower"); // io.openems.common.types
			final var channel = this.componentManager.getChannel(addr);

			if (channel instanceof IntegerReadChannel irc) {
				this.latestEssSetpoint = irc.value().orElse(0);
			} else {
													  
				this.latestEssSetpoint = 0;
			}
		} catch (Exception e) {
											 
			this.latestEssSetpoint = 0;
		}
	}

	/**
	 * Handles the State-Machine.
	 */
	private void handleStateMachine() {
		// WICHTIG: Nicht global auf UNDEFINED zurücksetzen – die Handler setzen
		// Start/Stop gezielt je nach Zustand.

									
		// this._setStartStop(StartStop.UNDEFINED);

					
		IntegerWriteChannel requestRelayState = null;
		IntegerReadChannel relaySequence = null;
		try {
			requestRelayState = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE);
//			relaySequence = this.channel(SensataBms.ChannelId.RELAY_SEQUENCE);
		} catch (IllegalArgumentException e1) {
							 
			this.logError(this.log, "Setting requestRelayState/relaySequence channels failed: " + e1.getMessage());
						
		}

		var context = new Context(this, requestRelayState, relaySequence);

		try {
																		  
			this.stateMachine.run(context);
																						
		} catch (OpenemsNamedException e) {
																					   
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}

	}


	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
																	   

		if (this.startStopTarget.getAndSet(value) != value) {
			// Start/Stop-Target geändert -> FSM in UNDEFINED setzen, damit sauber neu
			// eingestartet/gestoppt wird.
								  
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}
 

	@Override
	public StartStop getStartStopTarget() {
		return switch (this.config.startStop()) {
			case AUTO -> this.startStopTarget.get(); // read StartStop-Channel
			case START -> StartStop.START; // force START
			case STOP -> StartStop.STOP; // force STOP
			  
											   
			default -> StartStop.UNDEFINED;
	
		};
	}

	@Override
	public int getLatestEssSetpointW() {
		return this.latestEssSetpoint;
	}

	@Override
	public int getDeadbandW() {
		return DEADBAND;
	}

	@Override
	public String debugLog() {
		 
		return "Modbus Values: cap.:" + this.channel(Battery.ChannelId.CAPACITY).value().asString() + " max_Icharge: "
				+ this.channel(Battery.ChannelId.CHARGE_MAX_CURRENT).value().asString() + " max_Idischarge: "
				+ this.channel(Battery.ChannelId.DISCHARGE_MAX_CURRENT).value().asString() + " soc: "
				+ this.channel(Battery.ChannelId.SOC).value().asString() + " soh: "
				+ this.channel(Battery.ChannelId.SOH).value().asString() + " I: "
				+ this.channel(Battery.ChannelId.CURRENT).value().asString() + " min_Vc: "
				+ this.channel(Battery.ChannelId.MIN_CELL_VOLTAGE).value().asString() + " max_Vc: "
				+ this.channel(Battery.ChannelId.MAX_CELL_VOLTAGE).value().asString() + " V: "
				+ this.channel(Battery.ChannelId.VOLTAGE).value().asString() + "°C:"
				+this.channel(Battery.ChannelId.MAX_CELL_TEMPERATURE).value().asString() +"°C:"
				+this.channel(Battery.ChannelId.MIN_CELL_TEMPERATURE).value().asString();
	}
 
		   
										   
}
