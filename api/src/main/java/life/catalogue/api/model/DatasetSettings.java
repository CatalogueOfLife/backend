package life.catalogue.api.model;

import life.catalogue.api.jackson.SettingsDeserializer;
import life.catalogue.api.vocab.Setting;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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

  public Character getChar(Setting key) {
    return (Character) get(key);
  }

  public Boolean getBool(Setting key) {
    try {
      return (Boolean) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to boolean", key, get(key), e);
      return null;
    }
  }

  public boolean getBoolDefault(Setting key, boolean defaultValue) {
    var bool = getBool(key);
    return bool == null ? defaultValue : bool;
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

  public <T> List<T> getList(Setting key) {
    try {
      return (List<T>) get(key);
    } catch (Exception e) {
      LOG.warn("Failed to convert setting {}={} to list", key, get(key), e);
      return Collections.emptyList();
    }
  }

  @Override
  public void putAll(Map<? extends Setting, ?> m) {
    for (var en : m.entrySet()) {
      put(en.getKey(), en.getValue());
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
        if (key.getType().equals(Character.class) && value instanceof String) {
          String str = (String) value;
          if (str.length()==0) {
            return remove(key);
          } else if (str.length()==1) {
            return super.put(key, str.charAt(0));
          }
        }
        throw new IllegalArgumentException("value for " + key.name() + " not of expected type " + key.getType());
      }
    }
    return super.put(key, value);
  }

  public boolean has(Setting key) {
    return containsKey(key);
  }

  public boolean isEnabled(Setting key) {
    return containsKey(key) && getBool(key);
  }

  public boolean isDisabled(Setting key) {
    return !containsKey(key) || !getBool(key);
  }

  public void enable(Setting key) {
    put(key, true);
  }

  public void disable(Setting key) {
    put(key, false);
  }
}
