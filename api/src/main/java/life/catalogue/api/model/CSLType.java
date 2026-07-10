package life.catalogue.api.model;

/**
 * COL-owned copy of the CSL citation type vocabulary, replacing the compile dependency on
 * de.undercouch.citeproc.csl.CSLType in the slim api model. Constant names and toString() ids
 * mirror citeproc exactly so JSON serialisation is unchanged. See
 * http://docs.citationstyles.org/en/stable/specification.html#appendix-iii-types
 */
public enum CSLType {
  ARTICLE("article"),
  ARTICLE_MAGAZINE("article-magazine"),
  ARTICLE_NEWSPAPER("article-newspaper"),
  ARTICLE_JOURNAL("article-journal"),
  BILL("bill"),
  BOOK("book"),
  BROADCAST("broadcast"),
  CHAPTER("chapter"),
  DATASET("dataset"),
  DOCUMENT("document"),
  ENTRY("entry"),
  ENTRY_DICTIONARY("entry-dictionary"),
  ENTRY_ENCYCLOPEDIA("entry-encyclopedia"),
  FIGURE("figure"),
  GRAPHIC("graphic"),
  INTERVIEW("interview"),
  LEGISLATION("legislation"),
  LEGAL_CASE("legal_case"),
  MANUSCRIPT("manuscript"),
  MAP("map"),
  MOTION_PICTURE("motion_picture"),
  MUSICAL_SCORE("musical_score"),
  PAMPHLET("pamphlet"),
  PAPER_CONFERENCE("paper-conference"),
  PATENT("patent"),
  PERFORMANCE("performance"),
  PERIODICAL("periodical"),
  POST("post"),
  POST_WEBLOG("post-weblog"),
  PERSONAL_COMMUNICATION("personal_communication"),
  REPORT("report"),
  REVIEW("review"),
  REVIEW_BOOK("review-book"),
  SOFTWARE("software"),
  SONG("song"),
  SPEECH("speech"),
  STANDARD("standard"),
  THESIS("thesis"),
  TREATY("treaty"),
  WEBPAGE("webpage");

  private final String id;

  CSLType(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }

  /** Parses a CSL id string (e.g. "article-journal") into the enum, or null if unknown. */
  public static CSLType fromString(String value) {
    if (value == null) return null;
    String norm = value.trim().toUpperCase().replaceAll("[_ -]+", "_");
    try {
      return valueOf(norm);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
