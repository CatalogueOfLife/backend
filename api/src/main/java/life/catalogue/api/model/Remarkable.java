package life.catalogue.api.model;

import org.apache.commons.lang3.StringUtils;

/**
 *
 */
public interface Remarkable {

  String getRemarks();

  void setRemarks(String remarks);

  default void addRemarks(String remarks) {
    addRemarks(remarks, ';');
  }

  default void addRemarks(String remarks, char delim) {
    if (!StringUtils.isBlank(remarks)) {
      if (getRemarks() == null) {
        setRemarks(remarks.trim());
      } else {
        setRemarks(getRemarks() + delim + " " + remarks.trim());
      }
    }
  }

}
