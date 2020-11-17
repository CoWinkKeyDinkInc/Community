package dev.pgm.community.teleports;

import dev.pgm.community.feature.config.FeatureConfigImpl;
import org.bukkit.configuration.Configuration;

public class TeleportConfig extends FeatureConfigImpl {

  private static final String KEY = "teleports";

  public TeleportConfig(Configuration config) {
    super(KEY, config);
  }

  @Override
  public void reload(Configuration config) {
    super.reload(config); // TODO: Cooldowns, sounds message format, and other config settings
  }
}
