package life.catalogue.api.jackson;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;

import org.gbif.nameparser.api.NomCode;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SettingsDeserializerTest {

  @Test
  public void convertFromJSON() {
    Map<Setting, Object> map = new HashMap<>();
    map.put(Setting.NOMENCLATURAL_CODE, "botanical");
    map.put(Setting.DISTRIBUTION_GAZETTEER, null);
    SettingsDeserializer.convertFromJSON(map);

    assertNull(map.get(Setting.DISTRIBUTION_GAZETTEER));
  }

  /**
   * A setting removed from the enum, e.g. the former "sector ranks", must not break every reader of the dataset.
   */
  @Test
  public void unknownKeysAreIgnored() throws Exception {
    DatasetSettings ds = ApiModule.MAPPER.readValue(
      "{\"sector ranks\": [\"genus\"], \"nomenclatural code\": \"botanical\"}", DatasetSettings.class);
    assertEquals(1, ds.size());
    assertEquals(NomCode.BOTANICAL, ds.getEnum(Setting.NOMENCLATURAL_CODE));
  }
}