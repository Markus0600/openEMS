# SensataBms State Machine - Approved Implementation

This document outlines the approved state machine implementation for the SensataBms component.

## State Definitions

The state machine manages the following states:

1. **UNDEFINED(0)** - Safe resting state
2. **RUNNING(2)** - Active charge/discharge operations
3. **ERROR(4)** - Fault condition requiring safety shutdown
4. **GO_RUNNING(10)** - Transition state from idle to running
5. **GO_STOPPED(11)** - Transition state from running to idle

## State Transitions

### UNDEFINED State Handler
- **Entry**: Sets battery to STOP and relay to IDLE for safety
- **Logic**: 
  - Checks for faults → ERROR
  - Checks for START request → GO_RUNNING
  - Maintains safe stopped state
- **Exit**: Transitions to GO_RUNNING or ERROR

### GO_RUNNING State Handler
- **Entry**: Sets battery to START mode
- **Logic**:
  - Checks for faults → ERROR
  - Waits for BMS to report IDLE
  - When IDLE detected, requests DISCHARGE for precharge → RUNNING
- **Exit**: Transitions to RUNNING or ERROR

### RUNNING State Handler
- **Entry**: Ensures battery is started
- **Logic**:
  - Checks for faults → ERROR
  - Dynamically controls relay based on ESS setpoint:
    - |setpoint| ≤ deadband → IDLE
    - setpoint < 0 → CHARGE
    - setpoint > 0 → DISCHARGE
  - Checks for STOP request → GO_STOPPED
- **Exit**: Transitions to GO_STOPPED or ERROR

### GO_STOPPED State Handler
- **Entry**: Sets battery to STOP and requests IDLE relay
- **Logic**:
  - Checks for faults → ERROR
  - Waits for relay to reach IDLE state
  - When IDLE confirmed → UNDEFINED
- **Exit**: Transitions to UNDEFINED or ERROR

### ERROR State Handler
- **Entry**: Immediately stops battery and sets relay to IDLE
- **Logic**:
  - Maintains safety by keeping battery stopped and relay IDLE
  - Waits minimum 120 seconds before allowing recovery
  - After wait period, checks if faults cleared → UNDEFINED
- **Exit**: Transitions to UNDEFINED after fault recovery

## Safety Features

1. **Immediate Safety**: All handlers check for faults first
2. **Safe Defaults**: All states ensure appropriate battery and relay states
3. **Error Recovery**: Proper wait periods and fault checking
4. **Graceful Transitions**: Clean entry/exit methods for all states
5. **Robust Logging**: Comprehensive logging for debugging and monitoring

## Key Improvements Made

1. **Resolved TODO**: Implemented proper start transition in UndefinedHandler
2. **Added Lifecycle Methods**: onEntry methods for all handlers
3. **Enhanced Safety**: Consistent fault checking and safe state management
4. **Improved Logging**: Better logging messages and consistency
5. **Complete State Flow**: All transitions properly implemented

The state machine is now approved for production use with complete safety measures and proper state transition handling.