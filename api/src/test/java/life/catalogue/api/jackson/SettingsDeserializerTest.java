package life.catalogue.api.jackson;

import life.catalogue.api.vocab.Setting;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

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
}