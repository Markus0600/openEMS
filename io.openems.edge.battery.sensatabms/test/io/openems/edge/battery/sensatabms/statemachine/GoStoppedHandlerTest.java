package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;

/**
 * Tests for {@link GoStoppedHandler}.
 */
public class GoStoppedHandlerTest {

    private GoStoppedHandler handler;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.handler = new GoStoppedHandler();
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testTransitionToIdleWhenRelaySequenceConfirmed() throws OpenemsNamedException {
        // Setup: Relay sequence confirms IDLE state
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to IDLE
        assertEquals(State.IDLE, nextState);
        assertFalse("Battery should be stopped", battery.isStarted());
    }

    @Test
    public void testStaysInGoStoppedWhenRelaySequenceNotConfirmed() throws OpenemsNamedException {
        // Setup: Relay sequence not yet IDLE
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_STOPPED
        assertEquals(State.GO_STOPPED, nextState);
        assertFalse("Battery should be stopped", battery.isStarted());
    }

    @Test
    public void testRequestsIdleRelayStateWhenNotSet() throws OpenemsNamedException {
        // Setup: Relay sequence not IDLE, current state not IDLE
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Should request IDLE relay state
        assertEquals("Should request IDLE relay state", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testDoesNotRequestRelayWhenAlreadyIdle() throws OpenemsNamedException {
        // Setup: Relay sequence not IDLE but already requested
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE;

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Should not request relay again
        assertEquals("Should not request relay when already IDLE", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testTransitionToErrorWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Faults are present
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testStopsBatteryEvenWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Faults are present but handler should still stop battery
        battery.setStarted(true);
        battery.setHasFaults(true);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should be stopped (but will transition to ERROR)
        assertFalse("Battery should be stopped even with faults", battery.isStarted());
    }

    @Test
    public void testFaultAfterRelayConfirmation() throws OpenemsNamedException {
        // Setup: Relay sequence confirms IDLE but fault occurs
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR instead of IDLE
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testHandlesNullRelaySequence() throws OpenemsNamedException {
        // Setup: Relay sequence is null/undefined
        battery.setRelaySequence(null);
        battery.setHasFaults(false);

        // Execute - This should not throw an exception
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_STOPPED and handle gracefully
        assertEquals(State.GO_STOPPED, nextState);
        assertFalse("Battery should be stopped", battery.isStarted());
    }

    @Test
    public void testRelaySequenceWithDifferentValue() throws OpenemsNamedException {
        // Setup: Relay sequence has unexpected value
        battery.setRelaySequence(State.DISCHARGE.getValue());
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in GO_STOPPED (waiting for IDLE confirmation)
        assertEquals(State.GO_STOPPED, nextState);
    }

    @Test
    public void testEnsuresBatteryIsStopped() throws OpenemsNamedException {
        // Setup: Battery is started but should be stopped
        battery.setStarted(true);
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should be stopped
        assertFalse("Battery should be stopped in GO_STOPPED", battery.isStarted());
    }

    @Test
    public void testSuccessfulTransitionSequence() throws OpenemsNamedException {
        // Setup: Complete successful transition from RUNNING to IDLE
        battery.setStarted(true);
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;

        // Execute first cycle - should request IDLE and stay in GO_STOPPED
        State nextState1 = handler.runAndGetNextState(context);
        assertEquals(State.GO_STOPPED, nextState1);
        assertFalse("Battery should be stopped", battery.isStarted());
        assertEquals("Should request IDLE relay state", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());

        // Simulate relay confirmation
        battery.setRelaySequence(State.IDLE.getValue());
        context.currentRelayState = Status.IDLE;

        // Execute second cycle - should transition to IDLE
        State nextState2 = handler.runAndGetNextState(context);
        assertEquals(State.IDLE, nextState2);
    }
}