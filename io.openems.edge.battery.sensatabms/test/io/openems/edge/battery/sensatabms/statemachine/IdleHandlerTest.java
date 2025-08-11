package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;

/**
 * Tests for {@link IdleHandler}.
 */
public class IdleHandlerTest {

    private IdleHandler handler;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.handler = new IdleHandler();
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testStaysInIdleWhenStopTargetSet() throws OpenemsNamedException {
        // Setup: Battery target is STOP (normal idle state)
        battery.setStartStopTarget(StartStop.STOP);
        battery.setStarted(false);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in IDLE
        assertEquals(State.IDLE, nextState);
        assertFalse("Battery should remain stopped", battery.isStarted());
    }

    @Test
    public void testTransitionToGoRunningWhenStartRequested() throws OpenemsNamedException {
        // Setup: Battery target is START
        battery.setStartStopTarget(StartStop.START);
        battery.setStarted(false);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to GO_RUNNING
        assertEquals(State.GO_RUNNING, nextState);
    }

    @Test
    public void testTransitionToErrorWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Battery has faults
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(true);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR instead of GO_RUNNING
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testStopsBatteryWhenStarted() throws OpenemsNamedException {
        // Setup: Battery is started but should be stopped in IDLE
        battery.setStarted(true);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        handler.runAndGetNextState(context);

        // Verify: Battery should be stopped
        assertFalse("Battery should be stopped in IDLE state", battery.isStarted());
    }

    @Test
    public void testSetsRelayToIdleWhenStarted() throws OpenemsNamedException {
        // Setup: Battery is started, needs to be set to IDLE
        battery.setStarted(true);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING; // Not IDLE

        // Execute
        handler.onEntry(context);
        handler.runAndGetNextState(context);

        // Verify: Relay should be set to IDLE
        assertEquals("Should request IDLE relay state", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testDoesNotSetRelayWhenAlreadyIdle() throws OpenemsNamedException {
        // Setup: Battery is started, relay is already IDLE
        battery.setStarted(true);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE;

        // Execute
        handler.onEntry(context);
        handler.runAndGetNextState(context);

        // Verify: Relay request should not be changed
        assertEquals("Should not change relay when already IDLE", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testFaultTakesPrecedenceOverStartRequest() throws OpenemsNamedException {
        // Setup: START requested but faults present
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(true);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should go to ERROR instead of GO_RUNNING
        assertEquals("Faults should take precedence over START request", State.ERROR, nextState);
    }

    @Test
    public void testHandlesBatteryNotStarted() throws OpenemsNamedException {
        // Setup: Battery is not started, target is STOP
        battery.setStarted(false);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in IDLE
        assertEquals(State.IDLE, nextState);
        assertFalse("Battery should remain stopped", battery.isStarted());
    }

    @Test
    public void testHandlesUndefinedStartStopTarget() throws OpenemsNamedException {
        // Setup: Battery target is UNDEFINED (should be treated as STOP)
        battery.setStartStopTarget(StartStop.UNDEFINED);
        battery.setStarted(false);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in IDLE (UNDEFINED treated as STOP)
        assertEquals(State.IDLE, nextState);
    }
}