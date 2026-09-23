package life.catalogue.matching.person.harvest;

import java.util.List;

/**
 * An authority the registry is harvested from.
 */
public interface PersonSource {
  String name();

  List<PersonRecord> read() throws Exception;

  /**
   * @return what the source could not map, for the review report
   */
  default String stats() {
    return "";
  }
}
