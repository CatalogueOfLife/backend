package life.catalogue.api.vocab.area;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Objects;

/**
 * A generic area implementation that cannot be mapped to a more specific one.
 * With an identifier and (english) name.
 * Not enumerated.
 */
public class GenericArea implements Area {

  private Gazetteer gazetteer;
  private String id;
  private String name;

  public GenericArea(Gazetteer gazetteer, String id, String name) {
    this.gazetteer = gazetteer;
    this.id = StringUtils.trimToNull(id);
    this.name = StringUtils.trimToNull(name);
  }

  public GenericArea(Gazetteer gazetteer, String id) {
    this(gazetteer, id, null);
  }

  public GenericArea(String name) {
    this(Gazetteer.TEXT, null, name);
  }

  public GenericArea() {

  }

  public GenericArea(Area other) {
    this.gazetteer = other.getGazetteer();
    this.id = other.getId();
    this.name = other.getName();
  }

  @Override
  public Gazetteer getGazetteer() {
    return gazetteer;
  }

  public void setGazetteer(Gazetteer gazetteer) {
    this.gazetteer = gazetteer;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GenericArea)) return false;
    GenericArea area = (GenericArea) o;
    return gazetteer == area.gazetteer && Objects.equals(id, area.id) && Objects.equals(name, area.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gazetteer, id, name);
  }

  @Override
  public String toString() {
    if (gazetteer == Gazetteer.TEXT) {
      return name;
    } else {
      return gazetteer + ":" + id + (name != null ? " (" + name + ")" : "");
    }
  }
}
