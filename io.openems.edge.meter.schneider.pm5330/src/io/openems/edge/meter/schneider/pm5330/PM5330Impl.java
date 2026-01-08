package io.openems.edge.meter.schneider.pm5330;

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

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Implements the Schneider Electric PM5330 power meter.
 * 
 * <p>This meter is typically used as the Grid meter (HT Incomer) to measure
 * all incoming and outgoing energy at the grid connection point.
 * 
 * <p>Register values from PM5330:
 * <ul>
 * <li>Power: kW → converted to W (×1000)
 * <li>Voltage: V → converted to mV (×1000)
 * <li>Current: A → converted to mA (×1000)
 * <li>Frequency: Hz → converted to mHz (×1000)
 * <li>Energy: kWh → converted to Wh (×1000) - stored as S64
 * <li>Reactive Power: kVAr → converted to VAr (×1000)
 * </ul>
 */
@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Meter.Schneider.PM5330",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class PM5330Impl extends AbstractOpenemsModbusComponent
		implements PM5330, ElectricityMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private MeterType meterType = MeterType.GRID;

	public PM5330Impl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				PM5330.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.meterType = config.type();

		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,

				// ===== STRÖME (Register 3000-3011) =====
				// Current A, B, C, N, G (A → mA)
				new FC3ReadRegistersTask(3000, Priority.LOW,
						m(ElectricityMeter.ChannelId.CURRENT_L1, 
								new FloatDoublewordElement(3000), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.CURRENT_L2, 
								new FloatDoublewordElement(3002), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.CURRENT_L3, 
								new FloatDoublewordElement(3004), SCALE_FACTOR_3)),
				
				// Current Average
				new FC3ReadRegistersTask(3010, Priority.HIGH,
						m(ElectricityMeter.ChannelId.CURRENT, 
								new FloatDoublewordElement(3010), SCALE_FACTOR_3)),

				// ===== SPANNUNGEN L-N (Register 3028-3037) =====
				// Voltage A-N, B-N, C-N (V → mV)
				new FC3ReadRegistersTask(3028, Priority.LOW,
						m(ElectricityMeter.ChannelId.VOLTAGE_L1, 
								new FloatDoublewordElement(3028), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.VOLTAGE_L2, 
								new FloatDoublewordElement(3030), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.VOLTAGE_L3, 
								new FloatDoublewordElement(3032), SCALE_FACTOR_3)),
				
				// Voltage L-N Average
				new FC3ReadRegistersTask(3036, Priority.HIGH,
						m(ElectricityMeter.ChannelId.VOLTAGE, 
								new FloatDoublewordElement(3036), SCALE_FACTOR_3)),

				// ===== WIRKLEISTUNG (Register 3054-3061) =====
				// Real Power A, B, C, Total (kW → W)
				new FC3ReadRegistersTask(3054, Priority.HIGH,
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 
								new FloatDoublewordElement(3054), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, 
								new FloatDoublewordElement(3056), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, 
								new FloatDoublewordElement(3058), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER, 
								new FloatDoublewordElement(3060), SCALE_FACTOR_3)),

				// ===== BLINDLEISTUNG (Register 3062-3069) =====
				// Reactive Power A, B, C, Total (kVAr → VAr)
				new FC3ReadRegistersTask(3062, Priority.LOW,
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, 
								new FloatDoublewordElement(3062), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, 
								new FloatDoublewordElement(3064), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, 
								new FloatDoublewordElement(3066), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER, 
								new FloatDoublewordElement(3068), SCALE_FACTOR_3)),

				// ===== FREQUENZ (Register 3110-3111) =====
				// Frequency (Hz → mHz)
				new FC3ReadRegistersTask(3110, Priority.LOW,
						m(ElectricityMeter.ChannelId.FREQUENCY, 
								new FloatDoublewordElement(3110), SCALE_FACTOR_3)),

				// ===== ENERGIE (Register 3204-3211) =====
				// Energy Into Load (Import) - S64 (kWh → Wh)
				// Energy Out of Load (Export) - S64 (kWh → Wh)
				new FC3ReadRegistersTask(3204, Priority.LOW,
						m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 
								new SignedQuadruplewordElement(3204), SCALE_FACTOR_3),
						m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 
								new SignedQuadruplewordElement(3208), SCALE_FACTOR_3)));
	}

	@Override
	public String debugLog() {
		return "P:" + this.getActivePower().asString();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode));
	}

}