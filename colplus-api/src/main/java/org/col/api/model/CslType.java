package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CslType {
  ARTICLE("article"),
  ARTICLE_JOURNAL("article-journal"),
  ARTICLE_MAGAZINE("article-magazine"),
  ARTICLE_NEWSPAPER("article-newspaper"),
  BILL("bill"),
  BOOK("book"),
  BROADCAST("broadcast"),
  CHAPTER("chapter"),
  DATASET("dataset"),
  ENTRY("entry"),
  ENTRY_DICTIONARY("entry-dictionary"),
  ENTRY_ENCYCLOPEDIA("entry-encyclopedia"),
  FIGURE("figure"),
  GRAPHIC("graphic"),
  INTERVIEW("interview"),
  LEGAL_CASE("legal_case"),
  LEGISLATION("legislation"),
  MANUSCRIPT("manuscript"),
  MAP("map"),
  MOTION_PICTURE("motion_picture"),
  MUSICAL_SCORE("musical_score"),
  PAMPHLET("pamphlet"),
  PAPER_CONFERENCE("paper-conference"),
  PATENT("patent"),
  PERSONAL_COMMUNICATION("personal_communication"),
  POST("post"),
  POST_WEBLOG("post-weblog"),
  REPORT("report"),
  REVIEW("review"),
  REVIEW_BOOK("review-book"),
  SONG("song"),
  SPEECH("speech"),
  THESIS("thesis"),
  TREATY("treaty"),
  WEBPAGE("webpage");

  @JsonCreator
  public static CslType parse(String s) {
    if (s == null || (s = s.trim().toLowerCase()).isEmpty()) {
      return null;
    }
    for (CslType t : values()) {
      if (t.name.equals(s)) {
        return t;
      }
    }
    throw new IllegalArgumentException("Invalid CSL type: " + s);
  }

  private String name;

  private CslType(String name) {
    this.name = name;
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  /**
   * Converts the given string to a CSLType
   * 
   * @param str the string
   * @return the converted CSLType
   */
  public static CslType fromString(String str) {
    if (str.equals("article")) {
      return ARTICLE;
    }
    if (str.equals("article-journal") || str.equals("article journal")) {
      return ARTICLE_JOURNAL;
    }
    if (str.equals("article-magazine") || str.equals("article magazine")) {
      return ARTICLE_MAGAZINE;
    }
    if (str.equals("article-newspaper") || str.equals("article newspaper")) {
      return ARTICLE_NEWSPAPER;
    }
    if (str.equals("bill")) {
      return BILL;
    }
    if (str.equals("book")) {
      return BOOK;
    }
    if (str.equals("broadcast")) {
      return BROADCAST;
    }
    if (str.equals("chapter")) {
      return CHAPTER;
    }
    if (str.equals("dataset")) {
      return DATASET;
    }
    if (str.equals("entry")) {
      return ENTRY;
    }
    if (str.equals("entry-dictionary") || str.equals("entry dictionary")) {
      return ENTRY_DICTIONARY;
    }
    if (str.equals("entry-encyclopedia") || str.equals("entry encyclopedia")) {
      return ENTRY_ENCYCLOPEDIA;
    }
    if (str.equals("figure")) {
      return FIGURE;
    }
    if (str.equals("graphic")) {
      return GRAPHIC;
    }
    if (str.equals("interview")) {
      return INTERVIEW;
    }
    if (str.equals("legal_case")) {
      return LEGAL_CASE;
    }
    if (str.equals("legislation")) {
      return LEGISLATION;
    }
    if (str.equals("manuscript")) {
      return MANUSCRIPT;
    }
    if (str.equals("map")) {
      return MAP;
    }
    if (str.equals("motion_picture")) {
      return MOTION_PICTURE;
    }
    if (str.equals("musical_score")) {
      return MUSICAL_SCORE;
    }
    if (str.equals("pamphlet")) {
      return PAMPHLET;
    }
    if (str.equals("paper-conference") || str.equals("paper conference")) {
      return PAPER_CONFERENCE;
    }
    if (str.equals("patent")) {
      return PATENT;
    }
    if (str.equals("personal_communication")) {
      return PERSONAL_COMMUNICATION;
    }
    if (str.equals("post")) {
      return POST;
    }
    if (str.equals("post-weblog") || str.equals("post weblog")) {
      return POST_WEBLOG;
    }
    if (str.equals("report")) {
      return REPORT;
    }
    if (str.equals("review")) {
      return REVIEW;
    }
    if (str.equals("review-book") || str.equals("review book")) {
      return REVIEW_BOOK;
    }
    if (str.equals("song")) {
      return SONG;
    }
    if (str.equals("speech")) {
      return SPEECH;
    }
    if (str.equals("thesis")) {
      return THESIS;
    }
    if (str.equals("treaty")) {
      return TREATY;
    }
    if (str.equals("webpage")) {
      return WEBPAGE;
    }
    throw new IllegalArgumentException("Unknown CSLType: " + str);
  }

}
