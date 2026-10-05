package life.catalogue.db.type2;

import life.catalogue.api.model.SectorSettings;

import java.sql.SQLException;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Stores the settings of a sector profile as JSONB. Unknown properties are ignored.
 */
public class SectorSettingsTypeHandler extends JsonAbstractHandler<SectorSettings> {

  public SectorSettingsTypeHandler() {
    super("SectorSettings", new TypeReference<SectorSettings>() {});
  }

  @Override
  protected SectorSettings fromJson(String json) throws SQLException {
    SectorSettings s = super.fromJson(json);
    return s == null ? new SectorSettings() : s;
  }
}
