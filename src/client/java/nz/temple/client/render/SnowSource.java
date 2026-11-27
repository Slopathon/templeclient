package nz.temple.client.render;

import nz.temple.client.render.snow.SnowDrifts;

public interface SnowSource {
	SnowVolume.Settings snowVolume();
	SnowDrifts.Settings driftSettings();
}
