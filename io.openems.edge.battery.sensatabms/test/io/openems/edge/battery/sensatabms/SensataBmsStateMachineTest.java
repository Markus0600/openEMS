package io.openems.edge.battery.sensatabms;

import static io.openems.edge.battery.sensatabms.SensataBms.ChannelId.RELAY_SEQUENCE;
import static io.openems.edge.battery.sensatabms.statemachine.StateMachine.State.UNDEFINED;
import static io.openems.edge.battery.sensatabms.statemachine.StateMachine.State.GO_RUNNING;
import static io.openems.edge.battery.sensatabms.statemachine.StateMachine.State.RUNNING;

import org.junit.Test;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class SensataBmsStateMachineTest {

    @Test
    public void testBasicStateMachineFlow() throws Exception {
        var sut = new SensataBmsImpl();
        
        new ComponentTest(sut) //
            .addReference("cm", new DummyConfigurationAdmin()) //
            .addReference("setModbus", new DummyModbusBridge("modbus0")) //
            .activate(MyConfig.create() //
                    .setId("battery0") //
                    .setModbusId("modbus0") //
                    .setStartStop(StartStop.AUTO) //
                    .build()) //
            // Initially should be in UNDEFINED state
            .next(new TestCase() //
                    .output("battery0", Battery.ChannelId.STATE_MACHINE, UNDEFINED)) //
            .deactivate();
    }
}