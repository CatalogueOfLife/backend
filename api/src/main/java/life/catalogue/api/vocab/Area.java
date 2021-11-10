package life.catalogue.api.vocab;

/**
 * A geographic area with various implementations.
 */
public interface Area {

  Gazetteer getGazetteer();

  String getId();

  String getName();

  default String getGlobalId() {
    if (getId() != null && getGazetteer() != Gazetteer.TEXT) {
      return getGazetteer().locationID(getId());
    }
    return null;
  }

}