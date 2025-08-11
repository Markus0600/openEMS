package io.openems.edge.battery.sensatabms.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.sensatabms.Status;

/**
 * Tests for {@link Context}.
 */
public class ContextTest {

    private Context context;
    private MockSensataBms battery;

    @Before
    public void setUp() {
        this.battery = new MockSensataBms();
        this.context = new Context(
            this.battery,
            this.battery.getRequestRelayStateChannel(),
            this.battery.getRelaySequenceChannel()
        );
    }

    @Test
    public void testInitialState() {
        // Verify initial state
        assertEquals("Initial relay state should be UNDEFINED", 
            Status.UNDEFINED, context.getRequestRelayState());
        assertNotNull("Parent should be set", context.getParent());
    }

    @Test
    public void testSetRequestRelayStateSuccess() throws OpenemsNamedException {
        // Setup: Valid state change
        Status newState = Status.RUNNING;

        // Execute
        context.setRequestRelayState(newState);

        // Verify
        assertEquals("Relay state should be updated", newState, context.getRequestRelayState());
        assertEquals("Channel should receive new value", 
            newState.getValue(), 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testSetRequestRelayStateNoChangeWhenSame() throws OpenemsNamedException {
        // Setup: Set initial state
        Status initialState = Status.IDLE;
        context.setRequestRelayState(initialState);
        battery.getRequestRelayStateChannel().setNextWriteValue(null); // Reset

        // Execute: Set same state again
        context.setRequestRelayState(initialState);

        // Verify: Should not send new request
        assertEquals("Relay state should remain the same", initialState, context.getRequestRelayState());
        assertEquals("Should not send duplicate request", 
            null, 
            battery.getRequestRelayStateChannel().getNextWriteValue());
    }

    @Test
    public void testSetRequestRelayStateValidStates() throws OpenemsNamedException {
        // Test all valid states
        Status[] validStates = {
            Status.UNDEFINED, Status.IDLE, Status.RUNNING, 
            Status.DISCHARGE, Status.ERROR
        };

        for (Status state : validStates) {
            context.setRequestRelayState(state);
            assertEquals("Should accept valid state: " + state, 
                state, context.getRequestRelayState());
        }
    }

    @Test
    public void testGetRelaySequenceWithValue() {
        // Setup: Set relay sequence value
        int expectedValue = Status.RUNNING.getValue();
        battery.setRelaySequence(expectedValue);

        // Execute
        int actualValue = context.getRelaySequence();

        // Verify
        assertEquals("Should return relay sequence value", expectedValue, actualValue);
    }

    @Test
    public void testGetRelaySequenceWithNull() {
        // Setup: Null relay sequence
        battery.setRelaySequence(null);

        // Execute
        int actualValue = context.getRelaySequence();

        // Verify
        assertEquals("Should return UNDEFINED for null value", 
            Status.UNDEFINED.getValue(), actualValue);
    }

    @Test
    public void testGetRelaySequenceWithNullChannel() {
        // Setup: Create context with null relay sequence channel
        Context contextWithNullChannel = new Context(battery, 
            battery.getRequestRelayStateChannel(), null);

        // Execute
        int actualValue = contextWithNullChannel.getRelaySequence();

        // Verify
        assertEquals("Should return UNDEFINED for null channel", 
            Status.UNDEFINED.getValue(), actualValue);
    }

    @Test
    public void testSetRequestRelayStateWithNullChannel() throws OpenemsNamedException {
        // Setup: Create context with null request channel
        Context contextWithNullChannel = new Context(battery, null, 
            battery.getRelaySequenceChannel());

        // Execute: Should not throw exception
        contextWithNullChannel.setRequestRelayState(Status.RUNNING);

        // Verify: State should still be tracked internally
        assertEquals("Should track state even with null channel", 
            Status.RUNNING, contextWithNullChannel.getRequestRelayState());
    }

    @Test
    public void testStateTransitionSequence() throws OpenemsNamedException {
        // Test a typical state transition sequence
        
        // UNDEFINED -> IDLE
        context.setRequestRelayState(Status.IDLE);
        assertEquals(Status.IDLE, context.getRequestRelayState());
        
        // IDLE -> RUNNING
        context.setRequestRelayState(Status.RUNNING);
        assertEquals(Status.RUNNING, context.getRequestRelayState());
        
        // RUNNING -> IDLE (stopping)
        context.setRequestRelayState(Status.IDLE);
        assertEquals(Status.IDLE, context.getRequestRelayState());
        
        // IDLE -> ERROR (fault condition)
        context.setRequestRelayState(Status.ERROR);
        assertEquals(Status.ERROR, context.getRequestRelayState());
    }

    @Test
    public void testRelaySequenceAndRequestStateIndependence() throws OpenemsNamedException {
        // Setup: Set different values for request and sequence
        context.setRequestRelayState(Status.RUNNING);
        battery.setRelaySequence(Status.IDLE.getValue());

        // Verify: Both should maintain their independent values
        assertEquals("Request state should be RUNNING", 
            Status.RUNNING, context.getRequestRelayState());
        assertEquals("Sequence should be IDLE", 
            Status.IDLE.getValue(), context.getRelaySequence());
    }

    @Test
    public void testParentBatteryReference() {
        // Verify parent reference
        assertEquals("Parent should be the battery instance", battery, context.getParent());
    }

    @Test
    public void testContextStateConsistency() throws OpenemsNamedException {
        // Test that context maintains consistent state across operations
        
        // Initial state
        assertEquals(Status.UNDEFINED, context.getRequestRelayState());
        
        // Change state multiple times
        context.setRequestRelayState(Status.IDLE);
        context.setRequestRelayState(Status.RUNNING);
        context.setRequestRelayState(Status.DISCHARGE);
        context.setRequestRelayState(Status.ERROR);
        context.setRequestRelayState(Status.IDLE);
        
        // Final state should be IDLE
        assertEquals("Final state should be IDLE", Status.IDLE, context.getRequestRelayState());
    }

    @Test
    public void testLogMessageGeneration() throws OpenemsNamedException {
        // This tests that log messages are generated without crashing
        // Actual log verification would require capturing log output
        
        Status fromState = Status.IDLE;
        Status toState = Status.RUNNING;
        
        context.setRequestRelayState(fromState);
        context.setRequestRelayState(toState);
        
        // If we reach here, logging didn't cause exceptions
        assertEquals("State transition should complete", toState, context.getRequestRelayState());
    }
}