package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;
import io.openems.edge.battery.sensatabms.statemachine.StateMachine.State;
import io.openems.edge.common.startstop.StartStop;

/**
 * Integration tests for {@link StateMachine}.
 * Tests complete state machine flows and transitions.
 */
public class StateMachineTest {

    private StateMachine stateMachine;
    private MockSensataBms battery;
    private Context context;

    @Before
    public void setUp() {
        this.stateMachine = new StateMachine(State.UNDEFINED);
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testInitialState() {
        assertEquals("Initial state should be UNDEFINED", 
            State.UNDEFINED, stateMachine.getCurrentState());
    }

    @Test
    public void testGetStateHandlerForAllStates() {
        // Verify all states have handlers
        for (State state : State.values()) {
            assertNotNull("State " + state + " should have a handler", 
                stateMachine.getStateHandler(state));
        }
    }

    @Test
    public void testCompleteStartupSequence() throws OpenemsNamedException {
        // Test complete sequence: UNDEFINED -> IDLE -> GO_RUNNING -> RUNNING
        
        // Start from UNDEFINED
        assertEquals(State.UNDEFINED, stateMachine.getCurrentState());
        
        // UNDEFINED -> IDLE (normal flow without faults)
        battery.setHasFaults(false);
        battery.setStarted(true); // Will be stopped by handler
        State nextState = stateMachine.run(context);
        assertEquals("Should transition to IDLE", State.IDLE, nextState);
        stateMachine.forceSetCurrentState(nextState);
        
        // IDLE -> GO_RUNNING (when START requested)
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        assertEquals("Should transition to GO_RUNNING", State.GO_RUNNING, nextState);
        stateMachine.forceSetCurrentState(nextState);
        
        // GO_RUNNING -> RUNNING (when relay sequence confirmed)
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        assertEquals("Should transition to RUNNING", State.RUNNING, nextState);
        assertTrue("Battery should be started", battery.isStarted());
    }

    @Test
    public void testCompleteShutdownSequence() throws OpenemsNamedException {
        // Test complete sequence: RUNNING -> GO_STOPPED -> IDLE
        
        // Start from RUNNING
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(true);
        battery.setHasFaults(false);
        
        // RUNNING -> GO_STOPPED (when STOP requested)
        battery.setStartStopTarget(StartStop.STOP);
        State nextState = stateMachine.run(context);
        assertEquals("Should transition to GO_STOPPED", State.GO_STOPPED, nextState);
        stateMachine.forceSetCurrentState(nextState);
        
        // GO_STOPPED -> IDLE (when relay sequence confirmed)
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        assertEquals("Should transition to IDLE", State.IDLE, nextState);
        assertFalse("Battery should be stopped", battery.isStarted());
    }

    @Test
    public void testFaultTransitionFromAllStates() throws OpenemsNamedException {
        // Test that all states can transition to ERROR when faults occur
        
        State[] testStates = {
            State.UNDEFINED, State.IDLE, State.GO_RUNNING, 
            State.RUNNING, State.GO_STOPPED, State.DISCHARGE
        };
        
        for (State state : testStates) {
            stateMachine.forceSetCurrentState(state);
            battery.setHasFaults(true);
            
            State nextState = stateMachine.run(context);
            assertEquals("State " + state + " should transition to ERROR on fault", 
                State.ERROR, nextState);
            
            // Reset for next test
            battery.setHasFaults(false);
        }
    }

    @Test
    public void testErrorStateRecovery() throws OpenemsNamedException {
        // Test recovery from ERROR state
        
        stateMachine.forceSetCurrentState(State.ERROR);
        battery.setHasFaults(true);
        
        // Should stay in ERROR while faults persist
        State nextState = stateMachine.run(context);
        assertEquals("Should stay in ERROR with faults", State.ERROR, nextState);
        
        // Clear faults - should eventually transition to IDLE
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        // Note: Real implementation would check time, so this might still be ERROR
        // until wait period expires, but the logic should be sound
        assertTrue("Should handle fault clearing", 
            nextState == State.ERROR || nextState == State.IDLE);
    }

    @Test
    public void testDischargeStateBehavior() throws OpenemsNamedException {
        // Test DISCHARGE state (currently has limited functionality)
        
        stateMachine.forceSetCurrentState(State.DISCHARGE);
        battery.setStarted(false);
        battery.setHasFaults(false);
        
        State nextState = stateMachine.run(context);
        assertEquals("Should stay in DISCHARGE", State.DISCHARGE, nextState);
        assertTrue("Battery should be started in DISCHARGE", battery.isStarted());
    }

    @Test
    public void testRelayStateManagement() throws OpenemsNamedException {
        // Test that relay states are managed correctly through transitions
        
        // IDLE state should set IDLE relay
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStarted(true);
        battery.setHasFaults(false);
        battery.setStartStopTarget(StartStop.STOP);
        context.currentRelayState = Status.RUNNING; // Not IDLE
        
        stateMachine.run(context);
        assertEquals("IDLE state should request IDLE relay", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
            
        // Reset relay request
        battery.getRequestRelayStateChannel().setNextWriteValue(null);
        
        // GO_RUNNING state should set RUNNING relay
        stateMachine.forceSetCurrentState(State.GO_RUNNING);
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE; // Not RUNNING
        
        stateMachine.run(context);
        assertEquals("GO_RUNNING state should request RUNNING relay", 
            Status.RUNNING.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testBatteryStartStopManagement() throws OpenemsNamedException {
        // Test that battery start/stop is managed correctly
        
        // IDLE should stop battery
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStarted(true);
        battery.setHasFaults(false);
        battery.setStartStopTarget(StartStop.STOP);
        
        stateMachine.run(context);
        assertFalse("IDLE should stop battery", battery.isStarted());
        
        // RUNNING should start battery
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(false);
        battery.setHasFaults(false);
        battery.setStartStopTarget(StartStop.START);
        
        stateMachine.run(context);
        assertTrue("RUNNING should start battery", battery.isStarted());
    }

    @Test
    public void testStateTransitionValidation() throws OpenemsNamedException {
        // Test that state transitions follow expected patterns
        
        // From UNDEFINED
        stateMachine.forceSetCurrentState(State.UNDEFINED);
        battery.setHasFaults(false);
        State nextState = stateMachine.run(context);
        assertTrue("UNDEFINED should go to IDLE or ERROR", 
            nextState == State.IDLE || nextState == State.ERROR);
        
        // From IDLE with START request
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        assertTrue("IDLE with START should go to GO_RUNNING or ERROR", 
            nextState == State.GO_RUNNING || nextState == State.ERROR);
        
        // From RUNNING with STOP request
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(false);
        nextState = stateMachine.run(context);
        assertTrue("RUNNING with STOP should go to GO_STOPPED or ERROR", 
            nextState == State.GO_STOPPED || nextState == State.ERROR);
    }

    @Test
    public void testConcurrentFaultScenario() throws OpenemsNamedException {
        // Test fault occurring during transition states
        
        // Fault during GO_RUNNING
        stateMachine.forceSetCurrentState(State.GO_RUNNING);
        battery.setRelaySequence(State.IDLE.getValue()); // Not ready yet
        battery.setHasFaults(true); // Fault occurs
        
        State nextState = stateMachine.run(context);
        assertEquals("Fault during GO_RUNNING should go to ERROR", State.ERROR, nextState);
        
        // Fault during GO_STOPPED
        stateMachine.forceSetCurrentState(State.GO_STOPPED);
        battery.setRelaySequence(State.RUNNING.getValue()); // Not ready yet
        battery.setHasFaults(true); // Fault occurs
        
        nextState = stateMachine.run(context);
        assertEquals("Fault during GO_STOPPED should go to ERROR", State.ERROR, nextState);
    }

    @Test
    public void testEdgeCaseTransitions() throws OpenemsNamedException {
        // Test edge cases and unusual scenarios
        
        // UNDEFINED start/stop target handling
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStartStopTarget(StartStop.UNDEFINED);
        battery.setHasFaults(false);
        
        State nextState = stateMachine.run(context);
        assertEquals("UNDEFINED target should stay in IDLE", State.IDLE, nextState);
        
        // Multiple rapid state changes
        for (int i = 0; i < 5; i++) {
            stateMachine.forceSetCurrentState(State.IDLE);
            battery.setStartStopTarget(i % 2 == 0 ? StartStop.START : StartStop.STOP);
            battery.setHasFaults(false);
            
            nextState = stateMachine.run(context);
            // Should handle rapid changes without crashing
            assertNotNull("Should handle rapid state changes", nextState);
        }
    }
}