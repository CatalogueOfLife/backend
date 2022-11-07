package life.catalogue.api.vocab;

import java.net.URI;

/**
 * A geographic area with various implementations.
 */
public interface Area {

  Gazetteer getGazetteer();

  String getId();

  String getName();

  default URI getLink() {
    var g = getGazetteer();
    if (g != null) {
      return g.getAreaLink(getId());
    }
    return null;
  }

  default String getGlobalId() {
    if (getId() != null && getGazetteer() != Gazetteer.TEXT) {
      return getGazetteer().locationID(getId());
    }
    return null;
  }
}