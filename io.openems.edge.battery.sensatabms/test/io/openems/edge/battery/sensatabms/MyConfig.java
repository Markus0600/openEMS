package io.openems.edge.battery.sensatabms;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.common.startstop.StartStopConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String modbusId = null;
		private int modbusUnitId;
		
		private StartStopConfig startStop;
		
		private int chargeMaxVoltage;
		private int disChargeMinVoltage;
		private int innerResistance;
		private int minCellTemperature;
		private int maxCellTemperature;
		
		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public Builder setModbusUnitId(int modbusUnitId) {
			this.modbusUnitId = modbusUnitId;
			return this;
		}

		public Builder setStartStop(StartStopConfig startStop) {
			this.startStop = startStop;
			return this;
		}

		public Builder setChargeMaxVoltage(int chargeMaxVoltage) {
			this.chargeMaxVoltage = chargeMaxVoltage;
			return this;
		}

		public Builder setDisChargeMinVoltage(int disChargeMinVoltage) {
			this.disChargeMinVoltage = disChargeMinVoltage;
			return this;
		}

		public Builder setMinCellTemperature(int minCellTemperature) {
			this.minCellTemperature = minCellTemperature;
			return this;
		}

		public Builder setMaxCellTemperature(int maxCellTemperature) {
			this.maxCellTemperature = maxCellTemperature;
			return this;
		}

		public Builder setInnerResistance(int innerResistance) {
			this.innerResistance = innerResistance;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public String Modbus_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.modbus_id());
	}

	@Override
	public int modbusUnitId() {
		return this.builder.modbusUnitId;
	}

	@Override
	public StartStopConfig startStop() {
		return this.builder.startStop;
	}

	@Override
	public int disChargeMinVoltage() {
		return this.builder.disChargeMinVoltage;
	}

	@Override
	public int chargeMaxVoltage() {
		return this.builder.chargeMaxVoltage;
	}

	@Override
	public int innerResistance() {
		return this.builder.innerResistance;
	}

	@Override
	public int minCellTemperature() {
		return this.builder.minCellTemperature;
	}

	@Override
	public int maxCellTemperature() {
		return this.builder.maxCellTemperature;
	}
	
}