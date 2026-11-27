package com.mojang.blaze3d.vertex;

public class QuadInstance {
	private int color = -1;
	private int light;
	private int overlay;
	public void setColor(int argb) {
		color = argb;
	}
	public void setLightCoords(int light) {
		this.light = light;
	}
	public void setOverlayCoords(int overlay) {
		this.overlay = overlay;
	}
	public int color() {
		return color;
	}
	public int lightCoords() {
		return light;
	}
	public int overlayCoords() {
		return overlay;
	}
}
