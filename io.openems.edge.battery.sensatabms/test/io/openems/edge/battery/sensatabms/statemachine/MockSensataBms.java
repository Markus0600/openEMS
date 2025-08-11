package io.openems.edge.battery.sensatabms.statemachine;

import java.util.concurrent.CompletableFuture;

import io.openems.common.channel.Channel;
import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.battery.sensatabms.SensataBms;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;

/**
 * Mock implementation of SensataBms for testing purposes.
 */
public class MockSensataBms implements SensataBms {

    private boolean hasFaults = false;
    private boolean isStarted = false;
    private StartStop startStopTarget = StartStop.STOP;
    private MockIntegerWriteChannel requestRelayStateChannel;
    private MockIntegerReadChannel relaySequenceChannel;

    public MockSensataBms() {
        this.requestRelayStateChannel = new MockIntegerWriteChannel("REQUEST_RELAY_STATE");
        this.relaySequenceChannel = new MockIntegerReadChannel("RELAY_SEQUENCE");
    }

    // Test control methods
    public void setHasFaults(boolean hasFaults) {
        this.hasFaults = hasFaults;
    }

    public void setStarted(boolean isStarted) {
        this.isStarted = isStarted;
    }

    public void setStartStopTarget(StartStop startStopTarget) {
        this.startStopTarget = startStopTarget;
    }

    public void setRelaySequence(int value) {
        this.relaySequenceChannel.setNextValue(value);
    }

    public MockIntegerWriteChannel getRequestRelayStateChannel() {
        return this.requestRelayStateChannel;
    }

    public MockIntegerReadChannel getRelaySequenceChannel() {
        return this.relaySequenceChannel;
    }

    // SensataBms interface implementation
    @Override
    public StartStop getStartStopTarget() {
        return this.startStopTarget;
    }

    @Override
    public Channel<Integer> getRelayRequestStateChannel() {
        return this.requestRelayStateChannel;
    }

    // Battery interface implementation
    @Override
    public boolean hasFaults() {
        return this.hasFaults;
    }

    // StartStoppable interface implementation
    @Override
    public boolean isStarted() {
        return this.isStarted;
    }

    @Override
    public void _setStartStop(StartStop value) {
        this.isStarted = (value == StartStop.START);
    }

    @Override
    public CompletableFuture<Void> setStartStop(StartStop value) {
        this._setStartStop(value);
        return CompletableFuture.completedFuture(null);
    }

    // OpenemsComponent interface methods (minimal implementation)
    @Override
    public String id() {
        return "mockSensataBms0";
    }

    @Override
    public String alias() {
        return "Mock Sensata BMS";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ComponentManager getComponentManager() {
        return null;
    }

    @Override
    public Channel<?> _channel(ChannelId channelId) {
        if (channelId == SensataBms.ChannelId.REQUEST_RELAY_STATE) {
            return this.requestRelayStateChannel;
        } else if (channelId == SensataBms.ChannelId.RELAY_SEQUENCE) {
            return this.relaySequenceChannel;
        }
        return null;
    }

    @Override
    public void logDebug(String message) {
        System.out.println("DEBUG: " + message);
    }

    @Override
    public void logInfo(String message) {
        System.out.println("INFO: " + message);
    }

    @Override
    public void logWarn(String message) {
        System.out.println("WARN: " + message);
    }

    @Override
    public void logError(String message) {
        System.err.println("ERROR: " + message);
    }

    @Override
    public void _setFault(ChannelId channelId, boolean value) {
        // Mock implementation
    }

    @Override
    public void _setFault(Enum<?> channelId, boolean value) {
        // Mock implementation
    }

    @Override
    public Channel<Integer> getFaultChannel() {
        return null;
    }

    @Override
    public Value<Integer> getFault() {
        return Value.of(0);
    }

    @Override
    public Channel<Integer> getWarningChannel() {
        return null;
    }

    @Override
    public Value<Integer> getWarning() {
        return Value.of(0);
    }

    // Battery interface methods (minimal implementation)
    @Override
    public Channel<Integer> getVoltageChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getCurrentChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getCapacityChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getSocChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getSohChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getMinCellVoltageChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getMaxCellVoltageChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getMinCellTemperatureChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getMaxCellTemperatureChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getChargeMaxVoltageChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getChargeMaxCurrentChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getDischargeMaxCurrentChannel() {
        return null;
    }

    @Override
    public Channel<Integer> getDischargeMinVoltageChannel() {
        return null;
    }
}