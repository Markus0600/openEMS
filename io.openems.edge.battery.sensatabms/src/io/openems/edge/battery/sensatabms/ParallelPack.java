package io.openems.edge.battery.sensatabms;

import io.openems.common.types.OptionsEnum;

public enum ParallelPack implements OptionsEnum {
	IDLE(0, "Idle"), //
	CHARGE(1, "Charge"), //
	DISCHARGE(2, "Discharge"); //

	private final int value;
	private final String name;

	private ParallelPack(int value, String name) {
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
	public static ParallelPack valueOf(int value) {
		for (ParallelPack status : ParallelPack.values()) {
			if (status.value == value) {
				return status;
			}
		}
		return IDLE;

	}

	@Override
	public OptionsEnum getUndefined() {
		return IDLE;
	}

}
