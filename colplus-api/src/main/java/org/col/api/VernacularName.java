package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

/**
 *
 */
public class VernacularName {

  @JsonIgnore
  private Integer key;

  /**
   * The vernacular name
   */
  private String name;

  private Language language;

  private Country country;

}
