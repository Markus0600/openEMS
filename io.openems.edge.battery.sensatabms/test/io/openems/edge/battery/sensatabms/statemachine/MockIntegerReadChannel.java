package io.openems.edge.battery.sensatabms.statemachine;

import io.openems.common.channel.Level;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Mock implementation of IntegerReadChannel for testing.
 */
public class MockIntegerReadChannel implements IntegerReadChannel {

    private final String channelId;
    private Integer value = null;

    public MockIntegerReadChannel(String channelId) {
        this.channelId = channelId;
    }

    public void setNextValue(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return this.value;
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
        return Value.of(this.value);
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
    public void deactivate() {
        // Mock implementation
    }
}