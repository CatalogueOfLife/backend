package life.catalogue.api.vocab;

import java.net.URI;
import java.util.Objects;

/**
 * An area without any identifier and just an english name.
 * Not enumerated.
 */
public class AreaImpl implements Area {

  private Gazetteer gazetteer;
  private String id;
  private String name;

  public AreaImpl(Gazetteer gazetteer, String id, String name) {
    this.gazetteer = gazetteer;
    this.id = id;
    this.name = name;
  }

  public AreaImpl(Gazetteer gazetteer, String id) {
    this(gazetteer, id, null);
  }

  public AreaImpl(String name) {
    this(Gazetteer.TEXT, null, name);
  }

  public AreaImpl() {

  }

  public AreaImpl(Area other) {
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
  public URI getLink() {
    switch (gazetteer) {
      case MRGID:
        return URI.create("http://marineregions.org/mrgid/48213");
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AreaImpl)) return false;
    AreaImpl area = (AreaImpl) o;
    return gazetteer == area.gazetteer && Objects.equals(id, area.id) && Objects.equals(name, area.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gazetteer, id, name);
  }

  @Override
  public String toString() {
    return "AreaImpl{" +
           "gazetteer=" + gazetteer +
           ", id='" + id + '\'' +
           ", name='" + name + '\'' +
           '}';
  }
}
