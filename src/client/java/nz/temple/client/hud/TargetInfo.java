package nz.temple.client.hud;

import net.minecraft.world.entity.player.PlayerSkin;

public record TargetInfo(String name, PlayerSkin skin, float health, float maxHealth, float distance, int ping) {
}
