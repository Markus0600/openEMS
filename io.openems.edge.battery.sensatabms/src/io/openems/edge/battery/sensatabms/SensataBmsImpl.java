package io.openems.edge.battery.sensatabms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.KEEP_POSITIVE;

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
import io.openems.edge.battery.protection.BatteryProtection;
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
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.Channel;
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
	private BatteryProtection batteryProtection = null;

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
	
	/**
	 * Persistenter Context für die StateMachine - vermeidet Zustandsverlust zwischen Zyklen
	 */
	private Context persistentContext = null;

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
				Battery.ChannelId.values(), //
				BatteryProtection.ChannelId.values() //
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
		
	    this.batteryProtection = BatteryProtection.create(this) //
	            .applyBatteryProtectionDefinition(new BatteryProtectionDefinition(), this.componentManager) //
	            .build();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.log.info("SensataBmsImpl::deactivate called.");

		super.deactivate();

		// switch off relay if shutdown
		try {
			IntegerWriteChannel requestRelayState = this.channel(SensataBms.ChannelId.REQUEST_RELAY_STATE);
			requestRelayState.setNextWriteValue(Status.IDLE.getValue());
			this.log.info("Relay set to IDLE during deactivation.");
		} catch (Exception e) {
			this.log.error("Failed to set relay to IDLE on deactivate: " + e.getMessage(), e);

		}
		
		// Reset persistentContext on deactivation
		this.persistentContext = null;
	}

	// Modbus addresses used for communication with Sensata BMS - read only
	private static final int CAPACITY = 100; // remaining capacity, Sensata ID 45000
	private static final int CHARGE_MAX_CURRENT = 110; // max. charge current, Sensata ID 45004
	private static final int DISCHARGE_MAX_CURRENT = 120; // max. discharge current, Sensata ID 45005
	private static final int SOC = 140; // state of charge, Sensata ID 45015
	private static final int SOH = 160; // state of health, Sensata ID 45045
	private static final int CURRENT = 170; // pack current, Sensata ID 547
	private static final int MIN_CELL_VOLTAGE = 180; // min cell voltage, Sensata ID 10405
	private static final int MAX_CELL_VOLTAGE = 190; // max cell voltage, Sensata ID 10406
	private static final int VOLTAGE = 200; // cmu - sum of cell voltage, Sensata ID 11400
	private static final int MIN_CELL_TEMPERATURE = 210;
	private static final int MAX_CELL_TEMPERATURE = 220;
	private static final int RELAY_SEQUENCE = 230;
	private static final int RELAY_SEQUENCE_COMPLETED = 240;

	// Modbus addresses used for communication with Sensata BMS - write only
	private static final int REQUEST_RELAY_STATE = 100;

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		this.log.info("SensataBmsImpl::defineModbusProtocol called.");

								  
		return new ModbusProtocol(this,

				// Values required for the battery channel - read only
				new FC3ReadRegistersTask(//
						CAPACITY, //
						Priority.LOW, //
						m(Battery.ChannelId.CAPACITY, new FloatQuadruplewordElement(CAPACITY)) //
				), //
				new FC3ReadRegistersTask(//
						CHARGE_MAX_CURRENT, //
						Priority.LOW, //
						m(Battery.ChannelId.CHARGE_MAX_CURRENT, new FloatQuadruplewordElement(CHARGE_MAX_CURRENT)) //
				), //

				
				//ToDo: Check if values for Discharge current stay positive
				new FC3ReadRegistersTask(//
						DISCHARGE_MAX_CURRENT, //
						Priority.LOW, //
						m(Battery.ChannelId.DISCHARGE_MAX_CURRENT, new FloatQuadruplewordElement(DISCHARGE_MAX_CURRENT), KEEP_POSITIVE) //
				), //
				new FC3ReadRegistersTask(//
						SOC, //
						Priority.LOW, //
						m(Battery.ChannelId.SOC, new SignedWordElement(SOC), SCALE_FACTOR_MINUS_2) //
				), //
				new FC3ReadRegistersTask(//
						SOH, //
						Priority.LOW, //
						m(Battery.ChannelId.SOH, new FloatDoublewordElement(SOH)) //
				), //
				new FC3ReadRegistersTask(//
						CURRENT, //
						Priority.LOW, //
						m(Battery.ChannelId.CURRENT, new FloatQuadruplewordElement(CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						MIN_CELL_VOLTAGE, //
						Priority.LOW, //
						m(Battery.ChannelId.MIN_CELL_VOLTAGE, new FloatQuadruplewordElement(MIN_CELL_VOLTAGE), SCALE_FACTOR_3) //
				), //
				new FC3ReadRegistersTask(//
						MAX_CELL_VOLTAGE, //
						Priority.LOW, //
						m(Battery.ChannelId.MAX_CELL_VOLTAGE, new FloatQuadruplewordElement(MAX_CELL_VOLTAGE), SCALE_FACTOR_3) //
				), //
				new FC3ReadRegistersTask(//
						VOLTAGE, //
						Priority.LOW, //
						m(Battery.ChannelId.VOLTAGE, new FloatQuadruplewordElement(VOLTAGE)) //
				), //
				new FC3ReadRegistersTask(//
						MIN_CELL_TEMPERATURE, //
						Priority.LOW, //
						m(Battery.ChannelId.MIN_CELL_TEMPERATURE, new FloatQuadruplewordElement(MIN_CELL_TEMPERATURE)) //
				), //
				new FC3ReadRegistersTask(//
						MAX_CELL_TEMPERATURE, //
						Priority.LOW, //
						m(Battery.ChannelId.MAX_CELL_TEMPERATURE, new FloatQuadruplewordElement(MAX_CELL_TEMPERATURE)) //
				), //
				
				// Values required for Sensata itself - read only
				new FC3ReadRegistersTask(//
						RELAY_SEQUENCE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.RELAY_SEQUENCE, new UnsignedWordElement(RELAY_SEQUENCE)) //
				), //
				new FC3ReadRegistersTask(//
						RELAY_SEQUENCE_COMPLETED, //
						Priority.LOW, //
						m(SensataBms.ChannelId.RELAY_SEQUENCE_COMPLETED, new UnsignedWordElement(RELAY_SEQUENCE_COMPLETED)) //
				), //

				// Values required for Sensata itself - write only
				new FC6WriteRegisterTask(//
						REQUEST_RELAY_STATE, //
						m(SensataBms.ChannelId.REQUEST_RELAY_STATE, new UnsignedWordElement(REQUEST_RELAY_STATE)) //
				) //
			 
		);
	}

	@Override
	public void handleEvent(Event event) {
																																
		if (!this.isEnabled()) {
			return;
		}
							 
		if (EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE.equals(event.getTopic())) {
			
            Integer chargeMaxCurrent = this.getChargeMaxCurrent().orElse(null);
            if (chargeMaxCurrent != null) {
                Channel<Integer> bpChargeBmsChannel = this.channel(BatteryProtection.ChannelId.BP_CHARGE_BMS);
                bpChargeBmsChannel.setNextValue(chargeMaxCurrent);
            }
            
            Integer dischargeMaxCurrent = this.getDischargeMaxCurrent().orElse(null);
            if (dischargeMaxCurrent != null) {
                Channel<Integer> bpDischargeBmsChannel = this.channel(BatteryProtection.ChannelId.BP_DISCHARGE_BMS);
                bpDischargeBmsChannel.setNextValue(dischargeMaxCurrent);
            }
	            
	        if(this.batteryProtection != null) {
	        	this.batteryProtection.apply();
	        }
			
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
		
		IntegerWriteChannel requestRelayState = null;
		IntegerReadChannel relaySequence = null;
		IntegerReadChannel relaySequenceCompleted = null;
		try {
			requestRelayState = this.channel(SensataBms.ChannelId.REQUEST_RELAY_STATE);
			relaySequence = this.channel(SensataBms.ChannelId.RELAY_SEQUENCE);
			relaySequenceCompleted = this.channel(SensataBms.ChannelId.RELAY_SEQUENCE_COMPLETED);
			
		} catch (IllegalArgumentException e1) {						 
			this.logError(this.log, "Setting requestRelayState/relaySequence channels failed: " + e1.getMessage());
			return;
		}

		//Context nur einmal erstellen und danach wiederverwenden
		if(this.persistentContext == null) {
			this.persistentContext = new Context(this, requestRelayState, relaySequence, relaySequenceCompleted);
			this.log.info("Created persistent context for Statemachine");
		}
		
		try {
			//Statemachine mit dem bestehenden Context ausführen
			this.stateMachine.run(this.persistentContext);
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
				+ this.channel(Battery.ChannelId.VOLTAGE).value().asString() + " max_Temp: "
				+ this.channel(Battery.ChannelId.MAX_CELL_TEMPERATURE).value().asString() +" min_Temp: "
				+ this.channel(Battery.ChannelId.MIN_CELL_TEMPERATURE).value().asString() +" act State: "
				+ this.channel(SensataBms.ChannelId.RELAY_SEQUENCE).value().asString();	
				
	}
 
		   
										   
}
