package io.openems.edge.battery.sensatabms;

import io.openems.common.types.OptionsEnum;

public enum Status implements OptionsEnum {
	UNDEFINED(0, "Undefined"), //
	IDLE(1, "Idle"), //
	DISCHARGE(2, "Discharge"), //
	CHARGE(3, "Charge"), //
	ERROR(4, "Error");

	private final int value;
	private final String name;

	private Status(int value, String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	/**
	 * Returns a Status enum for a particular int value.
	 * 
	 * @param value (int value that represents a Status
	 * @return Status enum for the value
	 */
	public static Status valueOf(int value) {
		for (Status status : Status.values()) {
			if (status.value == value) {
				return status;
			}
		}
		return UNDEFINED;

	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}

}
