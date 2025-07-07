package io.openems.edge.battery.sensatabms;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;

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
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.NotImplementedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.FloatQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "SensataBMS", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class SensataBmsImpl extends AbstractOpenemsModbusComponent 
		implements SensataBms, Battery, ModbusComponent, OpenemsComponent {

	//private int soc;
	//private int soh;
	//private int voltage;
	//private int current;
	//private int capacityKWh;
	//private int chargeMaxVoltage;
	//private int chargeMaxCurrent;
	//private int disChargeMinVoltage;
	//private int disChargeMaxCurrent;
	//private int minCellTemperature;
	//private int maxCellTemperature;
	//private int minCellVoltage; // in mV
	//private int maxCellVoltage; // in mV
	//private int innerResistance;

	private Config config = null;
	private final Logger log = LoggerFactory.getLogger(SensataBmsImpl.class);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public SensataBmsImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				SensataBms.ChannelId.values(), //
				Battery.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if(super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id())) {
			return;
		}
		this.config = config;
		
		// Workaround: fill battery channel values which are currently not provided via Modbus with config values.
		IntegerWriteChannel channel = this.channel(Battery.ChannelId.CHARGE_MAX_VOLTAGE);
		try {
			channel.setNextWriteValue(config.chargeMaxVoltage());
		} catch (OpenemsNamedException e) {
			this.log.error(e.getMessage());
		}
		channel = this.channel(Battery.ChannelId.DISCHARGE_MIN_VOLTAGE);
		try {
			channel.setNextWriteValue(config.disChargeMinVoltage());
		} catch (OpenemsNamedException e) {
			this.log.error(e.getMessage());
		}
		channel = this.channel(Battery.ChannelId.INNER_RESISTANCE);
		try {
			channel.setNextWriteValue(config.innerResistance());
		} catch (OpenemsNamedException e) {
			this.log.error(e.getMessage());
		}
		channel = this.channel(Battery.ChannelId.MIN_CELL_TEMPERATURE);
		try {
			channel.setNextWriteValue(config.minCellTemperature());
		} catch (OpenemsNamedException e) {
			this.log.error(e.getMessage());
		}
		channel = this.channel(Battery.ChannelId.MAX_CELL_TEMPERATURE);
		try {
			channel.setNextWriteValue(config.maxCellTemperature());
		} catch (OpenemsNamedException e) {
			this.log.error(e.getMessage());
		}
		
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	// Modbus addresses used for communication with Sensata BMS
	private static final int CAPACITY 									= 100; // remaining capacity, Sensata ID 45000
	private static final int CHARGE_MAX_CURRENT 		= 110; // max. charge current, Sensata ID 45004
	private static final int DISCHARGE_MAX_CURRENT 	= 120; // max. discharge current, Sensata ID 45005
	private static final int SOC 												= 140; // state of charge, Sensata ID 45015
	private static final int SOH 												= 160; // state of health, Sensata ID 45045
	private static final int CURRENT									= 170; // pack current, Sensata ID 547
	private static final int MIN_CELL_VOLTAGE 				= 180; // min cell voltage, Sensata ID 10405
	private static final int MAX_CELL_VOLTAGE 				= 190; // max cell voltage, Sensata ID 10406
	private static final int VOLTAGE 									= 200; // cmu - sum of cell voltage, Sensata ID 11400
	
	@Override
	protected ModbusProtocol defineModbusProtocol() /*throws OpenemsException*/ {
		// TODO implement ModbusProtocol
		return new ModbusProtocol(this,
	            new FC3ReadRegistersTask(
	            		CAPACITY, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.CAPACITY, new FloatQuadruplewordElement(CAPACITY))
	                    ),
	            new FC3ReadRegistersTask(
	            		CHARGE_MAX_CURRENT, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.CHARGE_MAX_CURRENT, new FloatQuadruplewordElement(CHARGE_MAX_CURRENT))
	                    ),
	            new FC3ReadRegistersTask(
	            		DISCHARGE_MAX_CURRENT, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.DISCHARGE_MAX_CURRENT, new FloatQuadruplewordElement(DISCHARGE_MAX_CURRENT))
	                    ),
	            new FC3ReadRegistersTask(
	            		SOC, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.SOC, new SignedWordElement(SOC), SCALE_FACTOR_MINUS_2)
	                    ),
	            new FC3ReadRegistersTask(
	            		SOH, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.SOH, new FloatDoublewordElement(SOH))
	                    ),
	            new FC3ReadRegistersTask(
	            		CURRENT, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.CURRENT, new FloatQuadruplewordElement(CURRENT))
	                    ),
	            new FC3ReadRegistersTask(
	            		MIN_CELL_VOLTAGE, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.MIN_CELL_VOLTAGE, new FloatQuadruplewordElement(MIN_CELL_VOLTAGE), SCALE_FACTOR_3)
	                    ),
	            new FC3ReadRegistersTask(
	            		MAX_CELL_VOLTAGE, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.MAX_CELL_VOLTAGE, new FloatQuadruplewordElement(MAX_CELL_VOLTAGE), SCALE_FACTOR_3)
	                    ),
	            new FC3ReadRegistersTask(
	            		VOLTAGE, 
	                    Priority.LOW,
	                    m(Battery.ChannelId.VOLTAGE, new FloatQuadruplewordElement(VOLTAGE))
	                    )
				);
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		// TODO start stop is not implemented
		throw new NotImplementedException("Start Stop is currently not implemented for Sensata BMS.");
	}
	
	@Override
	public String debugLog() {
		return 
				"Modbus Values: " + this.channel(Battery.ChannelId.CAPACITY).value().asString()
				+ " " + this.channel(Battery.ChannelId.CHARGE_MAX_CURRENT).value().asString()
				+ " " + this.channel(Battery.ChannelId.DISCHARGE_MAX_CURRENT).value().asString()
				+ " " + this.channel(Battery.ChannelId.SOC).value().asString()
				+ " " + this.channel(Battery.ChannelId.SOH).value().asString()
				+ " " + this.channel(Battery.ChannelId.CURRENT).value().asString()
				+ " " + this.channel(Battery.ChannelId.MIN_CELL_VOLTAGE).value().asString()
				+ " " + this.channel(Battery.ChannelId.MAX_CELL_VOLTAGE).value().asString()
				+ " " + this.channel(Battery.ChannelId.VOLTAGE).value().asString()
				;
	}
	
	
}
