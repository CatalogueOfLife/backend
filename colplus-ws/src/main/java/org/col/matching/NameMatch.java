package org.col.matching;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.model.Name;


public class NameMatch {
  private Name name;
  private MatchType type;
  private List<NameMatch> alternatives = Lists.newArrayList();
  private String note;

}
