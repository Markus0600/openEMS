package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;

/**
 * Tests for {@link UndefinedHandler}.
 */
public class UndefinedHandlerTest {

    private UndefinedHandler handler;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.handler = new UndefinedHandler();
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testTransitionToIdleFromUndefined() throws OpenemsNamedException {
        // Setup: Battery is started, no faults
        battery.setStarted(true);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to IDLE
        assertEquals(State.IDLE, nextState);
        assertFalse("Battery should be stopped", battery.isStarted());
        assertEquals("Relay should be set to IDLE", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testTransitionToErrorWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Battery has faults
        battery.setStarted(true);
        battery.setHasFaults(true);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testStopsBatteryWhenStarted() throws OpenemsNamedException {
        // Setup: Battery is started
        battery.setStarted(true);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        handler.runAndGetNextState(context);

        // Verify: Battery should be stopped
        assertFalse("Battery should be stopped in UNDEFINED state", battery.isStarted());
    }

    @Test
    public void testSetsRelayToIdleWhenNotAlreadyIdle() throws OpenemsNamedException {
        // Setup: Battery is started, current relay state is not IDLE
        battery.setStarted(true);
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING; // Set to non-IDLE state

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
        // Setup: Battery is started, current relay state is already IDLE
        battery.setStarted(true);
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE;

        // Execute
        handler.onEntry(context);
        handler.runAndGetNextState(context);

        // Verify: Relay request should not be changed (null because already IDLE)
        assertEquals("Should not change relay request when already IDLE", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testHandlesBatteryNotStarted() throws OpenemsNamedException {
        // Setup: Battery is not started
        battery.setStarted(false);
        battery.setHasFaults(false);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should still transition to IDLE
        assertEquals(State.IDLE, nextState);
        assertFalse("Battery should remain stopped", battery.isStarted());
    }

    @Test
    public void testFaultTakesPrecedenceOverNormalFlow() throws OpenemsNamedException {
        // Setup: Battery is started but has faults
        battery.setStarted(true);
        battery.setHasFaults(true);

        // Execute
        handler.onEntry(context);
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should go to ERROR despite being started
        assertEquals("Faults should take precedence", State.ERROR, nextState);
    }
}