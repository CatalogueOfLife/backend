package life.catalogue.api.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import life.catalogue.api.jackson.SettingsDeserializer;
import life.catalogue.api.vocab.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = SettingsDeserializer.class)
public class DatasetSettings extends HashMap<Setting, Object> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetSettings.class);

  public static DatasetSettings of(Map<Setting, Object>map) {
    DatasetSettings ds = new DatasetSettings();
    ds.putAll(map);
    return ds;
  }

  public DatasetSettings() {
  }

  public DatasetSettings(Map<? extends Setting, ?> m) {
    super(m);
  }

  /**
   * Only visible for mybatis to populate the object.
   * Dont use for anything else!
   * @deprecated
   */
  public void setSettings(Map<Setting, Object> map){
    putAll(map);
  }

  public String getString(Setting key) {
    return (String) get(key);
  }

  public Boolean getBool(Setting key) {
    try {
      return (Boolean) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to boolean", key, get(key), e);
      return null;
    }
  }

  public Integer getInt(Setting key) {
    try {
      return (Integer) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to integer", key, get(key), e);
      return null;
    }
  }

  public URI getURI(Setting key) {
    try {
      return (URI) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to URI", key, get(key), e);
      return null;
    }
  }

  public <T extends Enum> T getEnum(Setting key) {
    try {
      return (T) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to enum", key, get(key), e);
      return null;
    }
  }

  public <T extends Enum> List<T> getEnumList(Setting key) {
    try {
      return (List<T>) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to enum", key, get(key), e);
      return null;
    }
  }

  @Override
  public Object put(Setting key, Object value) {
    if (value == null) {
      return remove(key);
    } else if (key.isMultiple()){
      if (!(value instanceof List)){
        throw new IllegalArgumentException("value must be a list of type " + key.getType());
      }
      List<?> list = (List) value;
      if (!list.isEmpty() && !key.getType().isInstance(list.get(0))){
        throw new IllegalArgumentException("list value not of expected type " + key.getType());
      }

    } else {
      if (!key.getType().isInstance(value)){
        throw new IllegalArgumentException("value not of expected type " + key.getType());
      }
    }
    return super.put(key, value);
  }

  public boolean has(Setting key) {
    return containsKey(key);
  }

}
