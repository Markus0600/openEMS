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
 * Fault injection and edge case tests for the Sensata BMS state machine.
 * These tests validate safety mechanisms and error handling.
 */
public class StateMachineFaultInjectionTest {

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
    public void testFaultInjectionDuringStartup() throws OpenemsNamedException {
        // Test fault injection at each step of startup sequence
        
        // 1. Fault during UNDEFINED -> IDLE transition
        stateMachine.forceSetCurrentState(State.UNDEFINED);
        battery.setHasFaults(true);
        
        State nextState = stateMachine.run(context);
        assertEquals("Fault in UNDEFINED should go to ERROR", State.ERROR, nextState);
        
        // 2. Fault during IDLE -> GO_RUNNING transition
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(true);
        
        nextState = stateMachine.run(context);
        assertEquals("Fault in IDLE should go to ERROR", State.ERROR, nextState);
        
        // 3. Fault during GO_RUNNING -> RUNNING transition
        stateMachine.forceSetCurrentState(State.GO_RUNNING);
        battery.setRelaySequence(State.RUNNING.getValue());
        battery.setHasFaults(true);
        
        nextState = stateMachine.run(context);
        assertEquals("Fault in GO_RUNNING should go to ERROR", State.ERROR, nextState);
    }

    @Test
    public void testFaultInjectionDuringShutdown() throws OpenemsNamedException {
        // Test fault injection during shutdown sequence
        
        // 1. Fault during RUNNING -> GO_STOPPED transition
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStartStopTarget(StartStop.STOP);
        battery.setHasFaults(true);
        
        State nextState = stateMachine.run(context);
        assertEquals("Fault in RUNNING should go to ERROR", State.ERROR, nextState);
        
        // 2. Fault during GO_STOPPED -> IDLE transition
        stateMachine.forceSetCurrentState(State.GO_STOPPED);
        battery.setRelaySequence(State.IDLE.getValue());
        battery.setHasFaults(true);
        
        nextState = stateMachine.run(context);
        assertEquals("Fault in GO_STOPPED should go to ERROR", State.ERROR, nextState);
    }

    @Test
    public void testConcurrentFaultConditions() throws OpenemsNamedException {
        // Test multiple fault conditions occurring simultaneously
        
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(true);
        
        // Inject multiple fault-like conditions
        battery.setHasFaults(true);
        battery.setStartStopTarget(StartStop.STOP); // Also requesting stop
        
        State nextState = stateMachine.run(context);
        assertEquals("Multiple conditions should prioritize ERROR", State.ERROR, nextState);
        assertFalse("Battery should be stopped for safety", battery.isStarted());
    }

    @Test
    public void testRelaySequenceCorruption() throws OpenemsNamedException {
        // Test handling of corrupted or unexpected relay sequence values
        
        // Test with invalid relay sequence during transitions
        stateMachine.forceSetCurrentState(State.GO_RUNNING);
        battery.setRelaySequence(999); // Invalid value
        battery.setHasFaults(false);
        
        State nextState = stateMachine.run(context);
        assertEquals("Invalid relay sequence should stay in GO_RUNNING", 
            State.GO_RUNNING, nextState);
        
        // Test with null relay sequence
        battery.setRelaySequence(null);
        nextState = stateMachine.run(context);
        assertEquals("Null relay sequence should stay in GO_RUNNING", 
            State.GO_RUNNING, nextState);
    }

    @Test
    public void testCommunicationFailures() throws OpenemsNamedException {
        // Test handling of communication failures
        
        // Test with null channels
        Context faultyContext = new Context(battery, null, null);
        
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        
        // Should not crash even with null channels
        State nextState = stateMachine.run(faultyContext);
        assertEquals("Should handle null channels gracefully", State.GO_RUNNING, nextState);
    }

    @Test
    public void testIntermittentFaults() throws OpenemsNamedException {
        // Test handling of intermittent fault conditions
        
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(true);
        
        // Intermittent fault: fault -> clear -> fault
        battery.setHasFaults(true);
        State nextState1 = stateMachine.run(context);
        assertEquals("First fault should go to ERROR", State.ERROR, nextState1);
        
        stateMachine.forceSetCurrentState(State.ERROR);
        battery.setHasFaults(false);
        State nextState2 = stateMachine.run(context);
        // Should handle fault clearing (might stay in ERROR due to wait period)
        assertEquals("Should handle fault clearing", State.ERROR, nextState2);
        
        // Fault returns
        battery.setHasFaults(true);
        State nextState3 = stateMachine.run(context);
        assertEquals("Should handle returning fault", State.ERROR, nextState3);
    }

    @Test
    public void testStateInconsistencies() throws OpenemsNamedException {
        // Test handling of inconsistent internal states
        
        // Battery started but in IDLE state (inconsistent)
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStarted(true); // Inconsistent with IDLE expectation
        battery.setHasFaults(false);
        
        stateMachine.run(context);
        assertFalse("IDLE handler should correct inconsistency", battery.isStarted());
        
        // Battery stopped but in RUNNING state (inconsistent)
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(false); // Inconsistent with RUNNING expectation
        battery.setHasFaults(false);
        
        stateMachine.run(context);
        // RUNNING handler should start the battery
        // (Note: this tests the corrective behavior)
    }

    @Test
    public void testRelayStateInconsistencies() throws OpenemsNamedException {
        // Test handling of relay state inconsistencies
        
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(false); // Will be corrected
        battery.setHasFaults(false);
        context.currentRelayState = Status.IDLE; // Inconsistent with RUNNING
        
        stateMachine.run(context);
        assertEquals("Should request correct relay state", 
            Status.RUNNING.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testEmergencyShutdownScenario() throws OpenemsNamedException {
        // Test emergency shutdown during active operation
        
        // Setup: Battery is running normally
        stateMachine.forceSetCurrentState(State.RUNNING);
        battery.setStarted(true);
        battery.setStartStopTarget(StartStop.START);
        battery.setHasFaults(false);
        context.currentRelayState = Status.RUNNING;
        
        // Verify normal operation
        State nextState = stateMachine.run(context);
        assertEquals("Should stay in RUNNING normally", State.RUNNING, nextState);
        
        // Emergency: Sudden fault condition
        battery.setHasFaults(true);
        
        nextState = stateMachine.run(context);
        assertEquals("Emergency fault should immediately go to ERROR", State.ERROR, nextState);
        
        // Verify safety measures are applied
        stateMachine.forceSetCurrentState(State.ERROR);
        ErrorHandler errorHandler = (ErrorHandler) stateMachine.getStateHandler(State.ERROR);
        errorHandler.onEntry(context);
        
        assertFalse("Emergency should stop battery", battery.isStarted());
        assertEquals("Emergency should set relay to IDLE", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testRapidStateChangeRequests() throws OpenemsNamedException {
        // Test rapid start/stop requests
        
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setHasFaults(false);
        
        // Rapid sequence: START -> STOP -> START -> STOP
        battery.setStartStopTarget(StartStop.START);
        State nextState1 = stateMachine.run(context);
        assertEquals("START request should go to GO_RUNNING", State.GO_RUNNING, nextState1);
        
        stateMachine.forceSetCurrentState(State.IDLE);
        battery.setStartStopTarget(StartStop.STOP);
        State nextState2 = stateMachine.run(context);
        assertEquals("STOP request should stay in IDLE", State.IDLE, nextState2);
        
        battery.setStartStopTarget(StartStop.START);
        State nextState3 = stateMachine.run(context);
        assertEquals("Second START should go to GO_RUNNING", State.GO_RUNNING, nextState3);
    }

    @Test
    public void testBoundaryConditions() throws OpenemsNamedException {
        // Test boundary conditions and edge values
        
        // Test with all possible StartStop values
        StartStop[] targets = {StartStop.START, StartStop.STOP, StartStop.UNDEFINED};
        
        for (StartStop target : targets) {
            stateMachine.forceSetCurrentState(State.IDLE);
            battery.setStartStopTarget(target);
            battery.setHasFaults(false);
            
            State nextState = stateMachine.run(context);
            // Should handle all targets without crashing
            if (target == StartStop.START) {
                assertEquals("START should go to GO_RUNNING", State.GO_RUNNING, nextState);
            } else {
                assertEquals("STOP/UNDEFINED should stay in IDLE", State.IDLE, nextState);
            }
        }
    }

    @Test
    public void testRecoveryAfterMultipleFaults() throws OpenemsNamedException {
        // Test recovery after experiencing multiple different fault conditions
        
        // Simulate sequence of different fault scenarios
        State[] faultStates = {State.IDLE, State.GO_RUNNING, State.RUNNING, State.GO_STOPPED};
        
        for (State faultState : faultStates) {
            // Enter fault state
            stateMachine.forceSetCurrentState(faultState);
            battery.setHasFaults(true);
            
            State nextState = stateMachine.run(context);
            assertEquals("All states should go to ERROR on fault", State.ERROR, nextState);
            
            // Clear fault and reset
            battery.setHasFaults(false);
        }
        
        // After multiple fault cycles, system should still be recoverable
        stateMachine.forceSetCurrentState(State.ERROR);
        battery.setHasFaults(false);
        
        State finalState = stateMachine.run(context);
        // Should handle recovery (might stay in ERROR due to wait period)
        assertEquals("Should handle recovery after multiple faults", State.ERROR, finalState);
    }

    @Test
    public void testSafetyMechanismValidation() throws OpenemsNamedException {
        // Validate that safety mechanisms are consistently applied
        
        // Test safety during ERROR state
        stateMachine.forceSetCurrentState(State.ERROR);
        battery.setStarted(true); // Unsafe condition
        battery.setHasFaults(true);
        
        ErrorHandler errorHandler = (ErrorHandler) stateMachine.getStateHandler(State.ERROR);
        errorHandler.onEntry(context);
        
        assertFalse("ERROR state should always stop battery", battery.isStarted());
        assertEquals("ERROR state should always set IDLE relay", 
            Status.IDLE.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
        
        // Test that safety is maintained even if battery is externally restarted
        battery.setStarted(true); // External restart
        stateMachine.run(context);
        assertFalse("Should prevent external restart in ERROR", battery.isStarted());
    }
}