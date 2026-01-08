package io.openems.edge.meter.schneider.pm5330;

import org.junit.Test;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class PM5330ImplTest {

	private static final String COMPONENT_ID = "meter0";
	private static final String MODBUS_ID = "modbus0";
	private static final int UNIT_ID = 51;

	@Test
	public void test() throws Exception {
		new ComponentTest(new PM5330Impl())
				.addReference("cm", new DummyConfigurationAdmin())
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID))
				.activate(MyConfig.create()
						.setId(COMPONENT_ID)
						.setModbusId(MODBUS_ID)
						.setModbusUnitId(UNIT_ID)
						.setType(MeterType.GRID)
						.build())
				.next(new TestCase());
	}

}