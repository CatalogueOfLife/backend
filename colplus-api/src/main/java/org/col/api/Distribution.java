package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.DistributionStatus;

import java.util.List;

/**
 *
 */
public class Distribution {

  @JsonIgnore
  private Integer key;

  private String area;

  private Gazetteer gazetteer;

  private DistributionStatus status;

  private List<Integer> referenceKeys;

}
