package life.catalogue.db.mapper.legacy.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LHigherName implements LName {
  private static Pattern RANK_MATCHER = Pattern.compile("^(.+[a-z]) [a-z]{1,6}\\. ([a-z]{2}.+)$");
  private static String BASE_URL = "https://www.catalogue.life/data/taxon/";
  protected String author;

  private String id;
  private String name;
  private Rank rank;
  private TaxonomicStatus status;
  private Boolean extinct;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    // remove all rank markers even for botanical names
    this.name = stripRank(name);
  }

  /**
   * Removes any infraspecific marker
   */
  static String stripRank(String name) {
    Matcher m = RANK_MATCHER.matcher(name);
    if (m.find()) {
      return m.replaceFirst(m.group(1) + " " + m.group(2));
    }
    return name;
  }

  @JsonProperty("name_html")
  public String getNameHtml() {
    String html;
    if (rank != null && rank.ordinal() >= Rank.GENUS.ordinal()) {
      html = inItalics(name);
    } else {
      html = name;
    }
    if (author != null) {
      html = html + " " + author;
    }
    return html;
  }

  static String inItalics(String x) {
    return "<i>" + x + "</i>";
  }

  @JsonProperty("rank")
  public String getRankName() {
    // Infraspecies
    if (rank.isInfraspecific()) {
      return "Infraspecies";
    }
    return StringUtils.capitalize(lc(rank));
  }

  @JsonIgnore
  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @JsonProperty("name_status")
  public String getNameStatus() {
    switch (status) {
      case ACCEPTED: return "accepted name";
    }
    return lc(status);
  }

  static String lc(Enum x) {
    return x.name().toLowerCase().replaceAll("_", " ");
  }

  @JsonIgnore
  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public String getUrl() {
    return BASE_URL + id;
  }

  @JsonProperty("is_extinct")
  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = StringUtils.trimToNull(author);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LHigherName)) return false;
    LHigherName that = (LHigherName) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(name, that.name) &&
      Objects.equals(author, that.author) &&
      rank == that.rank &&
      status == that.status &&
      Objects.equals(extinct, that.extinct);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, author, rank, status, extinct);
  }
}
