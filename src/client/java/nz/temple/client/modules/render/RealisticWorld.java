package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public final class RealisticWorld extends Module {
	private record Section(String title, BoolSetting toggle, Module feature) {
	}
	private static volatile RealisticWorld instance;
	private final List<Section> sections = new ArrayList<>();
	private boolean running;
	public RealisticWorld() {
		super("Realistic World", Category.COSMETICS, "Realistic water, wet ground, swaying foliage, grass blades and ray-traced "
				+ "lighting. Each part has its own switch.");
		section("Water", true, new RealisticWater());
		section("Wet ground", false, new WetGround());
		section("Foliage", true, new WavingFoliage());
		section("Grass", true, new RealisticGrass());
		section("Lighting", true, new RealisticLighting());
		instance = this;
	}
	private void section(String title, boolean on, Module feature) {
		group(title);
		BoolSetting toggle = add(new BoolSetting(title, on).describe(feature.description()));
		for (Setting<?> s : feature.allSettings()) {
			s.keyAs(title + ": " + s.name());
			s.andVisibleWhen(toggle::get);
			add(s);
		}
		toggle.addListener(this::sync);
		sections.add(new Section(title, toggle, feature));
	}
	@Override
	protected void onEnable() {
		running = true;
		sync();
	}
	@Override
	protected void onDisable() {
		running = false;
		sync();
	}
	private void sync() {
		for (Section s : sections) {
			s.feature().setEnabled(running && s.toggle().get());
		}
	}
	public static Module feature(String name) {
		RealisticWorld m = instance;
		if (m == null) {
			return null;
		}
		for (Section s : m.sections) {
			if (s.feature().name().equalsIgnoreCase(name)) {
				return s.feature();
			}
		}
		return null;
	}
	public static boolean setFeature(String name, boolean on) {
		RealisticWorld m = instance;
		if (m == null) {
			return false;
		}
		for (Section s : m.sections) {
			if (s.feature().name().equalsIgnoreCase(name)) {
				s.toggle().set(on);
				if (on) {
					m.setEnabled(true);
				}
				return true;
			}
		}
		return false;
	}
}
