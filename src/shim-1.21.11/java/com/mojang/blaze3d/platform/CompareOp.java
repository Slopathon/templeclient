package com.mojang.blaze3d.platform;

public enum CompareOp {
	ALWAYS_PASS,
	LESS_THAN,
	LESS_THAN_OR_EQUAL,
	EQUAL,
	NOT_EQUAL,
	GREATER_THAN_OR_EQUAL,
	GREATER_THAN,
	NEVER_PASS;
	public DepthTestFunction depthTest() {
		return switch (this) {
			case ALWAYS_PASS, NOT_EQUAL, NEVER_PASS -> DepthTestFunction.NO_DEPTH_TEST;
			case GREATER_THAN_OR_EQUAL -> DepthTestFunction.LEQUAL_DEPTH_TEST;
			case GREATER_THAN -> DepthTestFunction.LESS_DEPTH_TEST;
			case EQUAL -> DepthTestFunction.EQUAL_DEPTH_TEST;
			case LESS_THAN_OR_EQUAL, LESS_THAN -> DepthTestFunction.GREATER_DEPTH_TEST;
		};
	}
}
