package io.openems.edge.battery.sensatabms;

import io.openems.edge.battery.protection.force.ForceCharge;
import io.openems.edge.battery.protection.force.ForceDischarge;
import io.openems.edge.common.linecharacteristic.PolyLine;

public class BatteryProtectionDefinition implements io.openems.edge.battery.protection.BatteryProtectionDefinition {
	@Override
	public int getInitialBmsMaxEverChargeCurrent() {
		return Integer.MAX_VALUE; // keine Begrenzung des BMS charge current
	}

	@Override
	public int getInitialBmsMaxEverDischargeCurrent() {
		return Integer.MAX_VALUE; // keine Begrenzung des BMS discharge current
	}

	// Over voltage Protection
	@Override
	public PolyLine getChargeVoltageToPercent() {
		return PolyLine.empty();
	}

	// Low Voltage protection
	@Override
	public PolyLine getDischargeVoltageToPercent() {
		return PolyLine.empty();
	}

	@Override
	public PolyLine getChargeTemperatureToPercent() {
		return PolyLine.empty();
	}

	@Override
	public PolyLine getDischargeTemperatureToPercent() {
		return PolyLine.empty();
	}

	@Override
	public ForceDischarge.Params getForceDischargeParams() {
		return new ForceDischarge.Params(9999, 9998, 9997);
	}

	@Override
	public ForceCharge.Params getForceChargeParams() {
		return new ForceCharge.Params(0, 1, 2);

	}

	@Override
	public Double getMaxIncreaseAmperePerSecond() {
		return Double.MAX_VALUE; // [A] per second
	}

	@Override
	public PolyLine getChargeSocToPercent() {
		return PolyLine.empty();
	}

	@Override
	public PolyLine getDischargeSocToPercent() {
		return PolyLine.empty();
	}
}