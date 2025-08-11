# Sensata BMS State Machine Test Suite

This directory contains comprehensive unit tests for the Sensata BMS state machine implementation in OpenEMS Edge.

## Overview

The test suite validates the behavior, transitions, and safety mechanisms of the Sensata BMS state machine to ensure:
- **Safety**: All state transitions maintain battery safety
- **Reliability**: State transitions work correctly under normal and fault conditions  
- **Regression Prevention**: Changes to state handlers are validated
- **Fault Handling**: Error scenarios and recovery mechanisms are tested

## Test Structure

### Test Infrastructure
- **MockSensataBms.java** - Complete mock implementation of the SensataBms interface
- **MockIntegerWriteChannel.java** - Mock implementation for relay request channel
- **MockIntegerReadChannel.java** - Mock implementation for relay sequence channel

### State Handler Tests
Each state handler has dedicated unit tests covering all functionality:

#### UndefinedHandlerTest.java (8 tests)
- Transition to IDLE state
- Fault detection and ERROR transition
- Battery stop and relay control
- Edge case handling

#### IdleHandlerTest.java (9 tests)
- START request handling
- Transition to GO_RUNNING
- Fault detection during idle
- Relay state management

#### GoRunningHandlerTest.java (10 tests)
- Transition logic to RUNNING
- Relay sequence confirmation
- Timeout and fault handling
- Battery start control

#### RunningHandlerTest.java (11 tests)
- STOP request handling
- Transition to GO_STOPPED
- Continuous operation
- Fault detection during operation

#### GoStoppedHandlerTest.java (10 tests)
- Transition logic to IDLE
- Relay sequence confirmation
- Battery stop control
- Fault handling during shutdown

#### DischargeHandlerTest.java (9 tests)
- Discharge state behavior
- Missing exit logic identification
- Relay state management
- Continuous discharge operation

#### ErrorHandlerTest.java (11 tests)
- Safety mechanisms (120s wait period)
- Fault recovery logic
- Emergency battery stop
- Relay safety control

### Integration Tests

#### ContextTest.java (12 tests)
- Relay state management
- Channel communication
- State consistency
- Error handling

#### StateMachineTest.java (10 tests)
- Complete state transition sequences
- Startup sequence (UNDEFINED → IDLE → GO_RUNNING → RUNNING)
- Shutdown sequence (RUNNING → GO_STOPPED → IDLE)
- State handler integration

#### StateMachineFaultInjectionTest.java (12 tests)
- Safety mechanism validation
- Concurrent fault conditions
- Emergency shutdown scenarios
- Edge case handling

## Key Test Scenarios

### Normal Operation Flow
1. **Startup**: UNDEFINED → IDLE → GO_RUNNING → RUNNING
2. **Operation**: Continuous RUNNING state
3. **Shutdown**: RUNNING → GO_STOPPED → IDLE

### Fault Scenarios
- Fault injection at every state
- Concurrent fault conditions
- Intermittent faults
- Recovery after faults

### Safety Validation
- Emergency battery stop
- Relay safety positioning
- State inconsistency correction
- Communication failure handling

### Edge Cases
- Null/invalid relay sequences
- Rapid state change requests
- Communication failures
- Boundary conditions

## Running the Tests

The tests use JUnit 4 for consistency with the existing OpenEMS codebase. They can be run using:

```bash
# Run all Sensata BMS tests
./gradlew :io.openems.edge.battery.sensatabms:test

# Run specific test class
./gradlew :io.openems.edge.battery.sensatabms:test --tests "*.UndefinedHandlerTest"

# Run state machine tests only
./gradlew :io.openems.edge.battery.sensatabms:test --tests "*.statemachine.*"
```

## Test Coverage

The test suite provides comprehensive coverage:
- **102 total unit tests** across 10 test classes
- **All 7 state handlers** individually tested
- **Complete state machine integration** tested
- **Safety mechanisms** thoroughly validated
- **Fault injection and edge cases** covered

## Identified Issues

During test development, the following issues were identified:

### DischargeHandler Missing Exit Logic
The `DischargeHandler` currently lacks proper exit transition logic:
- No fault detection (commented out)
- No StartStop target checking
- Always stays in DISCHARGE state
- Potential safety concern

**Recommendation**: Add proper exit conditions for fault handling and state transitions.

### ErrorHandler Time Dependency
The `ErrorHandler` uses `Instant.now()` making it difficult to test time-based logic:
- 120-second wait period not easily testable
- Time-based transitions require real time delays

**Recommendation**: Consider dependency injection for clock/time source to improve testability.

## Mock Objects

The test suite uses comprehensive mock objects that simulate:
- Battery fault conditions
- Start/stop states
- Relay sequence confirmations
- Channel communication
- Component lifecycle

These mocks enable isolated testing without external dependencies.

## Safety Validation

Special attention is given to safety-critical aspects:
- Battery must be stopped in ERROR state
- Relay must be set to IDLE for safety
- Fault conditions take precedence over normal operations
- State inconsistencies are corrected
- Emergency shutdown is validated

## Contributing

When adding new functionality to the state machine:
1. Add corresponding unit tests
2. Update integration tests if needed
3. Add fault injection tests for safety validation
4. Ensure 100% test coverage of new code
5. Validate safety mechanisms

## Test Maintenance

- Tests should be updated when state machine logic changes
- Mock objects should be kept in sync with interface changes
- New edge cases should be added as they're discovered
- Performance impact of tests should be minimal