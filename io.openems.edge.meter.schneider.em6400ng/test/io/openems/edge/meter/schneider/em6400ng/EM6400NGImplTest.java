//package io.openems.edge.meter.schneider.em6400ng;
//
//import org.junit.Test;
//
//import io.openems.common.types.MeterType;
//import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
//import io.openems.edge.common.test.AbstractComponentTest.TestCase;
//import io.openems.edge.common.test.ComponentTest;
//import io.openems.edge.common.test.DummyConfigurationAdmin;
//
//public class EM6400NGImplTest {
//
//	private static final String COMPONENT_ID = "meter0";
//	private static final String MODBUS_ID = "modbus0";
//
//	@Test
//	public void test() throws Exception {
//		new ComponentTest(new EM6400NGImpl())
//				.addReference("cm", new DummyConfigurationAdmin())
//				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID))
//				.activate(MyConfig.create()
//						.setId(COMPONENT_ID)
//						.setModbusId(MODBUS_ID)
//						.setModbusUnitId(1)
//						.setType(MeterType.PRODUCTION)
//						.build())
//				.next(new TestCase());
//	}
//
//}

package io.openems.edge.meter.schneider.em6400ng;

import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.*;

import org.junit.Test;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class EM6400NGImplTest {

	private static final String COMPONENT_ID = "meter0";
	private static final String MODBUS_ID = "modbus0";
	private static final int UNIT_ID = 1;

	@Test
	public void test() throws Exception {
		new ComponentTest(new EM6400NGImpl())
				.addReference("cm", new DummyConfigurationAdmin())
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)
						
						// ===== ENERGIE (Register 2676-2679) =====
						// FLOAT32 Big Endian: 12345.67 kWh
						.withRegisters(2676, 0x4640, 0xE6B7)
						// Export Energy: 98765.43 kWh  
						.withRegisters(2678, 0x47C0, 0xE6B7)
						
						// ===== STRÖME (Register 3000-3011) =====
						// Current L1: 120.5 A
						.withRegisters(3000, 0x42F1, 0x0000)
						// Current L2: 118.3 A
						.withRegisters(3002, 0x42EC, 0x999A)
						// Current L3: 121.7 A
						.withRegisters(3004, 0x42F3, 0x6666)
						// Current Avg: 120.0 A
						.withRegisters(3010, 0x42F0, 0x0000)
						
						// ===== SPANNUNGEN (Register 3028-3037) =====
						// Voltage L1: 230.0 V
						.withRegisters(3028, 0x4366, 0x0000)
						// Voltage L2: 231.0 V
						.withRegisters(3030, 0x4367, 0x0000)
						// Voltage L3: 229.0 V
						.withRegisters(3032, 0x4365, 0x0000)
						// Voltage Avg: 230.0 V
						.withRegisters(3036, 0x4366, 0x0000)
						
						// ===== WIRKLEISTUNG (Register 3054-3061) =====
						// Active Power L1: 25.0 kW
						.withRegisters(3054, 0x41C8, 0x0000)
						// Active Power L2: 25.0 kW
						.withRegisters(3056, 0x41C8, 0x0000)
						// Active Power L3: 26.0 kW
						.withRegisters(3058, 0x41D0, 0x0000)
						// Active Power Total: 76.0 kW
						.withRegisters(3060, 0x4298, 0x0000)
						
						// ===== BLINDLEISTUNG (Register 3062-3069) =====
						// Reactive Power L1: 5.0 kVAr
						.withRegisters(3062, 0x40A0, 0x0000)
						// Reactive Power L2: 5.0 kVAr
						.withRegisters(3064, 0x40A0, 0x0000)
						// Reactive Power L3: 5.0 kVAr
						.withRegisters(3066, 0x40A0, 0x0000)
						// Reactive Power Total: 15.0 kVAr
						.withRegisters(3068, 0x4170, 0x0000)
						
						// ===== FREQUENZ (Register 3110-3111) =====
						// Frequency: 50.0 Hz
						.withRegisters(3110, 0x4248, 0x0000)
				)
				.activate(MyConfig.create()
						.setId(COMPONENT_ID)
						.setModbusId(MODBUS_ID)
						.setModbusUnitId(UNIT_ID)
						.setType(MeterType.PRODUCTION)
						.build())
				
				// Erster Zyklus - Werte werden gelesen
				.next(new TestCase("Cycle 1 - Read registers"))
				
				// Zweiter Zyklus - Werte prüfen
				.next(new TestCase("Cycle 2 - Verify values")
						// Wirkleistung: 76.0 kW = 76000 W
						.output(ACTIVE_POWER, 76000)
						// Spannung: 230.0 V = 230000 mV
						.output(VOLTAGE, 230000)
						// Strom: 120.0 A = 120000 mA
						.output(CURRENT, 120000)
						// Frequenz: 50.0 Hz = 50000 mHz
						.output(FREQUENCY, 50000)
				);
	}

}