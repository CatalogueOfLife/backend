package org.col.api.vocab;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appendix III - Types
 * http://docs.citationstyles.org/en/stable/specification.html#appendix_iii_types
 */
public enum CSLRefType {

    ARTICLE,
    ARTICLE_MAGAZINE,
    ARTICLE_NEWSPAPER,
    ARTICLE_JOURNAL,
    BILL,
    BOOK,
    BROADCAST,
    CHAPTER,
    DATASET,
    ENTRY,
    ENTRY_DICTIONARY,
    ENTRY_ENCYCLOPEDIA,
    FIGURE,
    GRAPHIC,
    INTERVIEW,
    LEGISLATION,
    LEGAL_CASE,
    MANUSCRIPT,
    MAP,
    MOTION_PICTURE,
    MUSICAL_SCORE,
    PAMPHLET,
    PAPER_CONFERENCE,
    PATENT,
    POST,
    POST_WEBLOG,
    PERSONAL_COMMUNICATION,
    REPORT,
    REVIEW,
    REVIEW_BOOK,
    SONG,
    SPEECH,
    THESIS,
    TREATY,
    WEBPAGE,
    MISC;

  private static final Logger LOG = LoggerFactory.getLogger(CSLRefType.class);

  @JsonCreator
  public static CSLRefType parse(String s) {
    if (StringUtils.isAllBlank(s)) {
      return null;
    }
    s = s.trim().toUpperCase().replaceAll("[_ -]+", "_");
    for (CSLRefType val : values()) {
      if (s.equals(val.name())) {
        return val;
      }
    }
    LOG.warn("Invalid CSLRefType: {}", s);
    return null;
  }

  @JsonValue
  public String toString() {
    switch (this) {
      case LEGAL_CASE:
      case MOTION_PICTURE:
      case MUSICAL_SCORE:
      case PERSONAL_COMMUNICATION:
        return name().replace('_', '-').toLowerCase();
      default:
        return name().toLowerCase();
    }
  }

}
