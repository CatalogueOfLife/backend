package life.catalogue.api.model;

import java.util.Map;

/**
 * The settings a sync of one sector uses, and for each setting the level it came from:
 * "default", "sector" or "profile:{id}"; a comma separated list of these for blocklists, which add up.
 */
public class EffectiveSectorSettings {
  private final SectorSettings settings;
  private final Map<String, String> sources;

  public EffectiveSectorSettings(SectorSettings settings, Map<String, String> sources) {
    this.settings = settings;
    this.sources = sources;
  }

  public SectorSettings getSettings() {
    return settings;
  }

  public Map<String, String> getSources() {
    return sources;
  }
}
