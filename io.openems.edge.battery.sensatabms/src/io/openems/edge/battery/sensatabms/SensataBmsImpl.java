package io.openems.edge.battery.sensatabms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.TRUE_IF_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.TRUE_IF_2;
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
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;																		   
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.DoubleReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.ShortReadChannel;
import io.openems.edge.common.channel.EnumReadChannel;
import io.openems.edge.common.channel.value.Value;
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
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, //
})
public class SensataBmsImpl extends AbstractOpenemsModbusComponent
		implements SensataBms, Battery, ModbusComponent, OpenemsComponent, EventHandler, StartStoppable {

	private Config config = null;
	private final Logger log = LoggerFactory.getLogger(SensataBmsImpl.class);
	private BatteryProtection batteryProtection = null;
	
	//Heart Beat for Sensata BMS Keep Alive
//	private static final int DEFAULT_HEART_BEAT = 1;
	

	// ESS setpoint tracking
	private volatile int latestEssSetpoint = 0;
	private volatile int latestEssState = 0;
	private volatile ParallelPack prevState = ParallelPack.CHARGE;

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
			IntegerWriteChannel requestRelayState = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE);
			requestRelayState.setNextWriteValue(ParallelPack.IDLE.getValue());
			this.log.info("Relay set to IDLE during deactivation.");
		} catch (Exception e) {
			this.log.error("Failed to set relay to IDLE on deactivate: " + e.getMessage(), e);

		}
		
	}

	// Modbus addresses used for communication with Sensata BMS - read only
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
	
	private static final int PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE = 420;
	private static final int PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE = 421;
	private static final int PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE = 422;
	private static final int PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE = 423;
	private static final int PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE = 424;
	
	private static final int PARALLEL_PACKS_PPAID6_RELAY_SEQUENCE = 430;
	private static final int PARALLEL_PACKS_PPAID7_RELAY_SEQUENCE = 431;
	private static final int PARALLEL_PACKS_PPAID8_RELAY_SEQUENCE = 432;
	private static final int PARALLEL_PACKS_PPAID9_RELAY_SEQUENCE = 433;
	private static final int PARALLEL_PACKS_PPAID10_RELAY_SEQUENCE = 434;
	
	private static final int PARALLEL_PACKS_PPAID1_PACK_CURRENT = 440;
	
	private static final int PARALLEL_PACKS_PPAID2_PACK_CURRENT = 450;
	
	private static final int PARALLEL_PACKS_PPAID3_PACK_CURRENT = 460;
	
	private static final int PARALLEL_PACKS_PPAID4_PACK_CURRENT = 470;
	
	private static final int PARALLEL_PACKS_PPAID5_PACK_CURRENT = 480;

	private static final int PARALLEL_PACKS_PPAID1_PACK_VOLTAGE = 490;
	
	private static final int PARALLEL_PACKS_PPAID2_PACK_VOLTAGE = 500;
	
	private static final int PARALLEL_PACKS_PPAID3_PACK_VOLTAGE = 510;
	
	private static final int PARALLEL_PACKS_PPAID4_PACK_VOLTAGE = 520;
	
	private static final int PARALLEL_PACKS_PPAID5_PACK_VOLTAGE = 530;
	
    // Faults/Warnings
    private static final int PARALLEL_PACKS_CELL_OVER_VOLTAGE = 560;
    private static final int PARALLEL_PACKS_CELL_UNDER_VOLTAGE = 561;
    private static final int PARALLEL_PACKS_CELL_OVER_TEMPERATURE = 562;
    private static final int PARALLEL_PACKS_CELL_UNDER_TEMPERATURE = 563;
    private static final int PARALLEL_PACKS_CURRENT_IN_TOO_HIGH = 564;
    private static final int PARALLEL_PACKS_CURRENT_OUT_TOO_HIGH = 565;
    private static final int PARALLEL_PACKS_SOA_WARNING = 566;
    private static final int PARALLEL_PACKS_SOA_VIOLATION = 567;

	
	// Modbus addresses used for communication with Sensata BMS - write only	
	private static final int PARALLEL_PACKS_REQUEST_RELAY_STATE  		= 600;
	private static final int HEART_BEAT 								= 601;

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		this.log.info("SensataBmsImpl::defineModbusProtocol called.");

								  
		return new ModbusProtocol(this,

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_STATE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_STATE, new UnsignedWordElement(PARALLEL_PACKS_STATE)),//
						m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS, new UnsignedWordElement(PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS))
				), //

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE, new SignedWordElement(PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE), SCALE_FACTOR_MINUS_2), //
						m(Battery.ChannelId.SOC, new SignedWordElement(PARALLEL_PACKS_AGGREGATED_SOC_TOTAL), SCALE_FACTOR_MINUS_2) //
				), //

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_SOH_TOTAL, //
						Priority.HIGH, //
						m(Battery.ChannelId.SOH, new FloatDoublewordElement(PARALLEL_PACKS_AGGREGATED_SOH_TOTAL)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE, new SignedDoublewordElement(PARALLEL_PACKS_AGGREGATED_CAPACITY_AVAILABLE)), //
						m(Battery.ChannelId.CAPACITY, new SignedDoublewordElement(PARALLEL_PACKS_AGGREGATED_CAPACITY_TOTAL)) //
				), //
				
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE)) //
				), //

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT, //
						Priority.HIGH, //
						m(BatteryProtection.ChannelId.BP_CHARGE_BMS, new FloatQuadruplewordElement(PARALLEL_PACKS_AGGREGATED_CHARGE_CURRENT)) //
				), //
				
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_DCLO, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_DCLO, new FloatQuadruplewordElement(PARALLEL_PACKS_AGGREGATED_DCLO), INVERT) //
				), //
				
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS)) //
				), //
				
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE, //
						Priority.HIGH, //
						m(Battery.ChannelId.MIN_CELL_TEMPERATURE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE), SCALE_FACTOR_MINUS_1), //
						m(Battery.ChannelId.MAX_CELL_TEMPERATURE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE) ,SCALE_FACTOR_MINUS_1) //
				), //

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE, //
						Priority.HIGH, //
						m(Battery.ChannelId.MIN_CELL_VOLTAGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE)), //
						m(Battery.ChannelId.MAX_CELL_VOLTAGE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE)) //
				), //

				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_CHARGE_COMPLETE_STATUS)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_TEMPERATURE_INDEX)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_TEMPERATURE_INDEX)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MIN_CELL_VOLTAGE_INDEX)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX, new UnsignedWordElement(PARALLEL_PACKS_AGGREGATED_MAX_CELL_VOLTAGE_INDEX)) //
				), //
				
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE, new UnsignedWordElement(PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE, new UnsignedWordElement(PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE, new UnsignedWordElement(PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE, new UnsignedWordElement(PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE)), //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE, new UnsignedWordElement(PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE)) //
				), //				
				
				//Voltages of all 5 Racks
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID1_PACK_VOLTAGE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_PACK_VOLTAGE, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID1_PACK_VOLTAGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID2_PACK_VOLTAGE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_PACK_VOLTAGE, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID2_PACK_VOLTAGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID3_PACK_VOLTAGE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_PACK_VOLTAGE, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID3_PACK_VOLTAGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID4_PACK_VOLTAGE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_PACK_VOLTAGE, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID4_PACK_VOLTAGE)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID5_PACK_VOLTAGE, //
						Priority.HIGH, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_PACK_VOLTAGE, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID5_PACK_VOLTAGE)) //
				), //
				
				
				//Current of all 5 Racks
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID1_PACK_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_PACK_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID1_PACK_CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID2_PACK_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_PACK_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID2_PACK_CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID3_PACK_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_PACK_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID3_PACK_CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID4_PACK_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_PACK_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID4_PACK_CURRENT)) //
				), //
				new FC3ReadRegistersTask(//
						PARALLEL_PACKS_PPAID5_PACK_CURRENT, //
						Priority.LOW, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_PACK_CURRENT, new FloatQuadruplewordElement(PARALLEL_PACKS_PPAID5_PACK_CURRENT)) //
				), //
				
				// Read warnings
			    new FC3ReadRegistersTask(//
			      PARALLEL_PACKS_CELL_OVER_VOLTAGE, //
			      Priority.HIGH, //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_OVER_VOLTAGE_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CELL_OVER_VOLTAGE), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_UNDER_VOLTAGE_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CELL_UNDER_VOLTAGE), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_OVER_TEMPERATURE_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CELL_OVER_TEMPERATURE), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_UNDER_TEMPERATURE_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CELL_UNDER_TEMPERATURE), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENT_IN_TOO_HIGH_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CURRENT_IN_TOO_HIGH), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENT_OUT_TOO_HIGH_WARNING, new UnsignedWordElement(PARALLEL_PACKS_CURRENT_OUT_TOO_HIGH), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_SOA_VIOLATION_WARNING, new UnsignedWordElement(PARALLEL_PACKS_SOA_WARNING), TRUE_IF_1), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_SOA_VIOLATION_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_SOA_VIOLATION), TRUE_IF_2) //
			    ), //
			    
			    // Read faults
			    new FC3ReadRegistersTask(//
			      PARALLEL_PACKS_CELL_OVER_VOLTAGE, //
			      Priority.HIGH, //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_OVER_VOLTAGE_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CELL_OVER_VOLTAGE), TRUE_IF_2), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_UNDER_VOLTAGE_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CELL_UNDER_VOLTAGE), TRUE_IF_2), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_OVER_TEMPERATURE_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CELL_OVER_TEMPERATURE), TRUE_IF_2), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CELL_UNDER_TEMPERATURE_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CELL_UNDER_TEMPERATURE), TRUE_IF_2), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENT_IN_TOO_HIGH_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CURRENT_IN_TOO_HIGH), TRUE_IF_2), //
			      m(SensataBms.ChannelId.PARALLEL_PACKS_CURRENT_OUT_TOO_HIGH_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_CURRENT_OUT_TOO_HIGH), TRUE_IF_2) //
			      //moved to task above for less read tasks
//			      m(SensataBms.ChannelId.PARALLEL_PACKS_SOA_VIOLATION_PROTECTION, new UnsignedWordElement(PARALLEL_PACKS_SOA_VIOLATION), TRUE_IF_2) //
			    ), //	
			    
			    
				// Values required for Sensata itself - write only
				new FC6WriteRegisterTask(//
						PARALLEL_PACKS_REQUEST_RELAY_STATE, //
						m(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE, new UnsignedWordElement(PARALLEL_PACKS_REQUEST_RELAY_STATE)) //
				), //
				
				new FC6WriteRegisterTask(//
						HEART_BEAT, //
						m(SensataBms.ChannelId.HEART_BEAT, new UnsignedWordElement(HEART_BEAT)) //
				) //
			 
		);
	}

	@Override
	public void handleEvent(Event event) {
																																
		if (!this.isEnabled()) {
			return;
		}
		
		ShortReadChannel relaySequence[] = {
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE)
		};
		
		DoubleReadChannel dVoltages[] = {
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_PACK_VOLTAGE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_PACK_VOLTAGE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_PACK_VOLTAGE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_PACK_VOLTAGE),
				this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_PACK_VOLTAGE)
		};
		
		ShortReadChannel numPacks = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS); 

		if(numPacks.value().isDefined() && numPacks.value().get() != null) {
		
			double dSum = 0;
			int iCount = 0;
			
			//calculation average voltage for correct power calculation (connected racks only) 
			for (int i= 0; (i<relaySequence.length) && (i<dVoltages.length); i++) {
				if(relaySequence[i].value().isDefined() &&
						relaySequence[i].value().get() != null &&
						//TODO Testen, da nur die BMS mit Relay Sequence2 berechnet werden sollen. vorher stand hier größer 0 -> auch die in IDLE werden mit berechnet -> falsche Spannung
						relaySequence[i].value().get() > 0 &&
						dVoltages[i].value().isDefined() &&
						dVoltages[i].value().get() != null ) {
					dSum += dVoltages[i].value().get();
					iCount++;
				}
			}
			double dAvg = (iCount > 0) ? (dSum / (double)iCount) : 0;
			this.log.info("Voltage for Battery Channel: {}", dAvg); 
			this.channel(Battery.ChannelId.VOLTAGE).setNextValue(dAvg);
			
			//calculation current discharge channel with value for connected racks			
			DoubleReadChannel dCurrent = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_DCLO);
			
			if(dCurrent.value().isDefined() && dCurrent.value().get() != null) {
				double dAvgCurrent = (iCount > 0) ? (dCurrent.value().get()/(double) iCount) : 0;
				this.channel(BatteryProtection.ChannelId.BP_DISCHARGE_BMS).setNextValue(dAvgCurrent);
			}	
		}
		
		switch(event.getTopic()) {
			case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE -> {
				this.log.info("Did Battery protection");
				this.batteryProtection.apply();
				break;
			}
			case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE -> {
				this.log.info("Did Statemachine, decision for Charging");
				this.handleStateMachine();
				this.updateLatestEssSetpointFromEssChannel();
				this.updateStateFromEssChannel();
				break;
			}
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
				this.log.warn("ESS ID is null/empty, defaulting latestEssState to 0");
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
	 * Reads the final ESS State via literal channel "StateMachine" from the
	 * component configured via ess_id. If unavailable, defaults to 0.
	 */
	private void updateStateFromEssChannel() {
	    try {
	        final String essId = (this.config != null) ? this.config.ess_id() : null;
	        if (essId == null || essId.isBlank()) {
	            this.log.warn("ESS ID is null/empty, defaulting ESS state to 0");
	            this.latestEssState = 0;
	            return;
	        }

	        final ChannelAddress addr = new ChannelAddress(essId, "StateMachine"); // StateMachine channel
	        final var channel = this.componentManager.getChannel(addr);
	        
			if (channel instanceof EnumReadChannel erc) {
	            this.latestEssState = erc.value().orElse(0);
	            this.log.debug("Got ESS state: {}", this.latestEssState);
	        } else {
	            this.log.warn("ESS StateMachine channel is not an IntegerReadChannel, defaulting to 0");
	            this.latestEssState = 0;
	        }
	    } catch (Exception e) {
	        this.log.error("Error reading ESS StateMachine: {}", e.getMessage());
	        this.latestEssState = 0;
	    }
	}
	
	/**
	 * Handles the State-Machine.
	 */
	private void handleStateMachine() {
		
		IntegerWriteChannel requestRelayState = null;
//		IntegerWriteChannel heartbeatChannel = null;
		
		ShortReadChannel relaySequence1 = null;
		ShortReadChannel relaySequence2 = null;
		ShortReadChannel relaySequence3 = null;
		ShortReadChannel relaySequence4 = null;
		ShortReadChannel relaySequence5 = null;
		ShortReadChannel numPacks = null;

		try {
			//0=idle, 1=charge, 2=discharge
			requestRelayState = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_REQUEST_RELAY_STATE);
			this.log.info("State from request Relay: {}",requestRelayState );
			
//			heartbeatChannel = this.channel(SensataBms.ChannelId.HEART_BEAT);
			
			//0=none, 1=inactive, 2=sequence1, 3=sequence2, 4=error
			relaySequence1 = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE);
			relaySequence2 = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE);
			relaySequence3 = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE);
			relaySequence4 = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE);
			relaySequence5 = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE);
			
			numPacks = this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_CURRENT_CONNECTIONS);
			
	
//			heartbeatChannel.setNextWriteValue(DEFAULT_HEART_BEAT);
//			this.logInfo(this.log, "HeartBeat: " + DEFAULT_HEART_BEAT);
			
		} catch (IllegalArgumentException e1) {
			this.logError(this.log, "Setting requestRelayState/relaySequence channels failed: " + e1.getMessage());
			return;
		}

		var context = new Context(this, requestRelayState, relaySequence1,relaySequence2, relaySequence3, relaySequence4, relaySequence5, numPacks);
		try {																		  
			this.stateMachine.run(context);																					
		} catch (OpenemsNamedException e) {																					   
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
	}


	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		if (this.startStopTarget.getAndSet(value) != value) {				  
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
	public int getLatestEssState() {
		return this.latestEssState;
	}
	
	@Override
	public ParallelPack getPrevState() {
		return this.prevState;
	}
	
	@Override
	public ParallelPack setPrevState(ParallelPack state) {
		this.prevState = state;
		return state;
	}
	
	@Override
	public int getChargeCurrent() {
		return (int) this.channel(BatteryProtection.ChannelId.BP_CHARGE_BMS).value().get();
	}
	@Override
	public int getDischargeCurrent() {
		return (int) this.channel(BatteryProtection.ChannelId.BP_DISCHARGE_BMS).value().get();
	}


	@Override
	public String debugLog() {
		 
		return "Modbus Values: cap.:" + this.channel(Battery.ChannelId.CAPACITY).value().asString() + " max_Icharge: "
				+ this.channel(BatteryProtection.ChannelId.BP_CHARGE_BMS).value().asString() + " max_Idischarge: "
				+ this.channel(BatteryProtection.ChannelId.BP_DISCHARGE_BMS).value().asString() + " soc: "
				+ this.channel(Battery.ChannelId.SOC).value().asString() + " soh: "
				+ this.channel(Battery.ChannelId.SOH).value().asString() + " min_Vc: "
				+ this.channel(Battery.ChannelId.MIN_CELL_VOLTAGE).value().asString() + " max_Vc: "
				+ this.channel(Battery.ChannelId.MAX_CELL_VOLTAGE).value().asString() + " max_Temp: "
				+ this.channel(Battery.ChannelId.MAX_CELL_TEMPERATURE).value().asString() +" min_Temp: "
				+ this.channel(Battery.ChannelId.MIN_CELL_TEMPERATURE).value().asString() +" act State: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_STATE).value().asString()	+ " det. packs: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_CURRENTLY_DETECTED_PACKS).value().asString()+ " soc av.: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SOC_AVAILABLE).value().asString()+ " num packs disch.: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_DISCHARGE_MODE).value().asString()+ " num packs charge: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_CHARGE_MODE).value().asString()+ " num missed disch.: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_DISCHARGE).value().asString()+ " num missed charge: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_NUMBER_PACKS_MISSED_CHARGE).value().asString()+ " bal. stat.: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_BALANCING_STATUS).value().asString()+ " contactor: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_CONTACTOR_WELD_STATUS).value().asString()+ " sequence: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID1_RELAY_SEQUENCE).value().asString()
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID2_RELAY_SEQUENCE).value().asString()
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID3_RELAY_SEQUENCE).value().asString()
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID4_RELAY_SEQUENCE).value().asString()
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_PPAID5_RELAY_SEQUENCE).value().asString()+ " actual system state: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_AGGREGATED_SYSTEM_STATE).value().asString()+ " Cell overvoltage warning: "
				+ this.channel(SensataBms.ChannelId.PARALLEL_PACKS_CELL_OVER_VOLTAGE_WARNING).value().asString()  
				;
	}
 		   										   
}
