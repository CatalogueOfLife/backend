package life.catalogue.metadata.zenodo;

import life.catalogue.api.model.DOI;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ZenodoResource {
  DOI doi;
  Links links;
  DOI conceptdoi;
  LocalDateTime created;
  LocalDateTime updated;
  String conceptrecid;
  Integer revision;
  Integer id;
  Metadata metadata;

  public static class Links {
    String self;
    DOI doi;
    DOI conceptdoi;
    URI conceptbadge;
    URI html;
    URI latest_html;
    URI badge;
    URI latest;
  }

  public static class Agent {
    String orcid;
    String affiliation;
    String type;
    String name;
  }

  public static class License {
    String id;
  }

  public static class Identifier {
    String scheme;
    String identifier;
    String relation;
    String resource_type;
  }

  public static class Relations {
    List<VersionRel> version;
    String identifier;
    String relation;
    String resource_type;
  }

  public static class PID {
    String pid_type;
    String pid_value;
  }

  public static class VersionRel {
    Integer count;
    Integer index;
    PID parent;
    boolean is_last;
    PID last_child;
  }

  public static class Grant {
    String code;
    Links links;
    String title;
    String acronym;
    String program;
    Funder funder;
  }

  public static class Funder {
    DOI doi;
    List<String> acronyms;
    String name;
    Links links;
  }

  public static class Metadata {
    DOI doi;
    String version;
    List<Agent> contributors;
    String title;
    License license;
    List<Identifier> related_identifiers;
    String notes;
    Relations relations;
    String language;
    List<Grant> grants;
    List<String> keywords;
    LocalDate publication_date;
    List<Agent> creators;
    String access_right;
    String description;
  }
}
