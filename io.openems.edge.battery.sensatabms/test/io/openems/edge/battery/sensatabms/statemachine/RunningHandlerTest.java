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
 * Tests for {@link RunningHandler}.
 */
public class RunningHandlerTest {

    private RunningHandler handler;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.handler = new RunningHandler();
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testStaysInRunningWhenStartTargetSet() throws OpenemsNamedException {
        // Setup: Battery target is START (normal running state)
        battery.setStartStopTarget(StartStop.START);
        battery.setStarted(true);
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in RUNNING
        assertEquals(State.RUNNING, nextState);
        assertTrue("Battery should remain started", battery.isStarted());
    }

    @Test
    public void testTransitionToGoStoppedWhenStopRequested() throws OpenemsNamedException {
        // Setup: Battery target is STOP
        battery.setStartStopTarget(StartStop.STOP);
        battery.setStarted(true);
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to GO_STOPPED
        assertEquals(State.GO_STOPPED, nextState);
    }

    @Test
    public void testTransitionToErrorWhenFaultsPresent() throws OpenemsNamedException {
        // Setup: Battery has faults
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR instead of staying in RUNNING
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testStartsBatteryWhenNotStarted() throws OpenemsNamedException {
        // Setup: Battery is not started but should be in RUNNING
        battery.setStarted(false);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should be started
        assertTrue("Battery should be started in RUNNING state", battery.isStarted());
    }

    @Test
    public void testSetsRelayToRunningWhenNotStarted() throws OpenemsNamedException {
        // Setup: Battery is not started, needs relay to be set to RUNNING
        battery.setStarted(false);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE; // Not RUNNING

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Relay should be set to RUNNING
        assertEquals("Should request RUNNING relay state", 
            Status.RUNNING.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testDoesNotSetRelayWhenAlreadyRunning() throws OpenemsNamedException {
        // Setup: Battery is not started, relay is already RUNNING
        battery.setStarted(false);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Relay request should not be changed
        assertEquals("Should not change relay when already RUNNING", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testFaultTakesPrecedenceOverStopRequest() throws OpenemsNamedException {
        // Setup: STOP requested but faults present
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should go to ERROR instead of GO_STOPPED
        assertEquals("Faults should take precedence over STOP request", State.ERROR, nextState);
    }

    @Test
    public void testHandlesUndefinedStartStopTarget() throws OpenemsNamedException {
        // Setup: Battery target is UNDEFINED (should be treated as current state)
        battery.setStartStopTarget(StartStop.UNDEFINED);
        battery.setStarted(true);
        battery.setHasFaults(false);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should stay in RUNNING (UNDEFINED doesn't trigger transition)
        assertEquals(State.RUNNING, nextState);
    }

    @Test
    public void testMaintainsStartedStateWhenAlreadyStarted() throws OpenemsNamedException {
        // Setup: Battery is already started and should remain started
        battery.setStarted(true);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);

        // Execute
        handler.runAndGetNextState(context);

        // Verify: Battery should remain started
        assertTrue("Battery should remain started", battery.isStarted());
    }

    @Test
    public void testFaultAfterStopRequest() throws OpenemsNamedException {
        // Setup: STOP requested, then fault occurs
        battery.setStartStopTarget(StartStop.STOP);
        battery.setStarted(true);
        battery.setHasFaults(true);

        // Execute
        State nextState = handler.runAndGetNextState(context);

        // Verify: Should transition to ERROR (fault takes precedence)
        assertEquals(State.ERROR, nextState);
    }

    @Test
    public void testContinuousRunningOperation() throws OpenemsNamedException {
        // Setup: Normal running operation
        battery.setStartStopTarget(StartStop.START);
        battery.setStarted(true);
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;

        // Execute multiple cycles
        State nextState1 = handler.runAndGetNextState(context);
        State nextState2 = handler.runAndGetNextState(context);
        State nextState3 = handler.runAndGetNextState(context);

        // Verify: Should consistently stay in RUNNING
        assertEquals(State.RUNNING, nextState1);
        assertEquals(State.RUNNING, nextState2);
        assertEquals(State.RUNNING, nextState3);
        assertTrue("Battery should remain started", battery.isStarted());
    }
}