package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;

/**
 * Tests for {@link GoRunningHandler}.
 */
public class GoRunningHandlerTest {

    private GoRunningHandler handler;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.handler = new GoRunningHandler();
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testTransitionToRunningWhenRelaySequenceConfirmed() throws OpenemsNamedException {
        // Setup: Relay sequence confirms RUNNING state
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to RUNNING
        assertEquals(State.RUNNING, nextState);
        assertTrue("Battery should be started", battery.isStarted());
    }

    @Test
    public void testStaysInGoRunningWhenRelaySequenceNotConfirmed() throws OpenemsNamedException {
        // Setup: Relay sequence not yet RUNNING
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_RUNNING
        assertEquals(State.GO_RUNNING, nextState);
        assertTrue("Battery should be started", battery.isStarted());
    }

    @Test
    public void testRequestsRunningRelayStateWhenNotSet() throws OpenemsNamedException {
        // Setup: Relay sequence not RUNNING, current state not RUNNING
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE;

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Should request RUNNING relay state
        assertEquals("Should request RUNNING relay state", 
            Status.RUNNING.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testDoesNotRequestRelayWhenAlreadyRunning() throws OpenemsNamedException {
        // Setup: Relay sequence not RUNNING but already requested
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Should not request relay again
        assertEquals("Should not request relay when already RUNNING", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testTransitionToErrorWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Faults are present
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testStartsBatteryEvenWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Faults are present but handler should still start battery
        battery.setStarted(false);
        battery.setHasFaults(true);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should be started (but will transition to ERROR)
        assertTrue("Battery should be started even with faults", battery.isStarted());
    }

    @Test
    public void testFaultAfterRelayConfirmation() throws OpenemsNamedException {
        // Setup: Relay sequence confirms RUNNING but fault occurs
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR instead of RUNNING
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testHandlesNullRelaySequence() throws OpenemsNamedException {
        // Setup: Relay sequence is null/undefined
        battery.setRelaySequence(null);
        battery.setHasFaults(false);

        // Execute - This should not throw an exception
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_RUNNING and handle gracefully
        assertEquals(State.GO_RUNNING, nextState);
        assertTrue("Battery should be started", battery.isStarted());
    }

    @Test
    public void testRelaySequenceWithDifferentValue() throws OpenemsNamedException {
        // Setup: Relay sequence has unexpected value
        battery.setRelaySequence(State.DISCHARGE.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_RUNNING (waiting for RUNNING confirmation)
        assertEquals(State.GO_RUNNING, nextState);
    }

    @Test
    public void testEnsuresBatteryIsStarted() throws OpenemsNamedException {
        // Setup: Battery is not started
        battery.setStarted(false);
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should be started
        assertTrue("Battery should be started in GO_RUNNING", battery.isStarted());
    }
}