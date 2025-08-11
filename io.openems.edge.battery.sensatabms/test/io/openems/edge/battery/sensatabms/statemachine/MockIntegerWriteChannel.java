package io.openems.edge.battery.sensatabms.statemachine;

import java.util.List;
import java.util.function.Consumer;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Mock implementation of IntegerWriteChannel for testing.
 */
public class MockIntegerWriteChannel implements IntegerWriteChannel {

    private final String channelId;
    private Integer nextWriteValue = null;
    private Integer value = null;

    public MockIntegerWriteChannel(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public void setNextWriteValue(Integer value) throws OpenemsNamedException {
        this.nextWriteValue = value;
        this.value = value; // For testing, immediately apply the value
    }

    public Integer getNextWriteValue() {
        return this.nextWriteValue;
    }

    public Integer getValue() {
        return this.value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public ChannelId channelId() {
        return () -> channelId;
    }

    @Override
    public OpenemsComponent getComponent() {
        return null;
    }

    @Override
    public ChannelAddress address() {
        return new ChannelAddress("test", channelId);
    }

    @Override
    public Value<Integer> value() {
        return Value.of(this.value);
    }

    @Override
    public Value<Integer> getNextValue() {
        return Value.of(this.nextWriteValue);
    }

    @Override
    public String channelDoc() {
        return "Mock channel for testing";
    }

    @Override
    public Level getLevel() {
        return Level.INFO;
    }

    @Override
    public void _setLevel(Level level) {
        // Mock implementation
    }

    @Override
    public WriteChannel<Integer> onSetNextWrite(Consumer<Integer> callback) {
        return this;
    }

    @Override
    public List<Consumer<Integer>> getOnSetNextWrites() {
        return List.of();
    }

    @Override
    public void deactivate() {
        // Mock implementation
    }
}