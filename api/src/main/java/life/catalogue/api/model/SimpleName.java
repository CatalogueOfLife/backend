package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Comparator;
import java.util.Objects;

import javax.validation.constraints.NotNull;

/**
 * A small class representing a name usage with an id. It can act as a reference to a scientific name in a dataset.
 * It combines the source usage ID with the full scientific name in order to best deal with changing identifiers in sources.
 */
public class SimpleName implements Comparable<SimpleName>, RankedID {
  private final static Comparator<String> nullSafeStringComparator = Comparator.nullsLast(String::compareTo);
  private final static Comparator<Enum> nullSafeEnumComparator = Comparator.nullsLast(Enum::compareTo);

  static final Comparator<SimpleName> NATURAL_ORDER =
      Comparator.comparing(SimpleName::getRank, nullSafeEnumComparator)
          .thenComparing(SimpleName::getName, nullSafeStringComparator)
          .thenComparing(SimpleName::getAuthorship, nullSafeStringComparator)
          .thenComparing(SimpleName::getPhrase, nullSafeStringComparator)
          .thenComparing(SimpleName::getStatus, nullSafeEnumComparator);

  private String id;

  @NotNull
  private String name;
  private String authorship;
  private String phrase;
  @NotNull
  private Rank rank;
  private NomCode code;
  private TaxonomicStatus status;
  private String parent;

  public SimpleName() {}

  public SimpleName(SimpleName other) {
    this.id = other.id;
    this.name = other.name;
    this.authorship = other.authorship;
    this.phrase = other.phrase;
    this.rank = other.rank;
    this.code = other.code;
    this.status = other.status;
    this.parent = other.parent;
  }

  public SimpleName(String id) {
    this.id = id;
  }

  public SimpleName(String id, String name, Rank rank) {
    this.id = id;
    this.name = name;
    this.rank = rank;
  }

  public SimpleName(String id, String name, String authorship, Rank rank) {
    this.id = id;
    this.name = name;
    this.authorship = authorship;
    this.rank = rank;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  public String getPhrase() {
    return phrase;
  }

  public void setPhrase(String phrase) {
    this.phrase = phrase;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public NomCode getCode() {
    return code;
  }

  public void setCode(NomCode code) {
    this.code = code;
  }

  public String getLabel() {
    return appendFullName(new StringBuilder(), false).toString();
  }

  public String getLabelHtml() {
    return appendFullName(new StringBuilder(), true).toString();
  }


  private StringBuilder appendFullName(StringBuilder sb, boolean html) {
    sb.append(html ? Name.scientificNameHtml(name, rank) : name);
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    if (phrase != null) {
      sb.append(" ");
      sb.append(phrase);
    }
    return sb;
  }

  public StringBuilder toStringBuilder() {
    StringBuilder sb = new StringBuilder();
    if (status != null) {
      sb.append(status);
      sb.append(" ");
    }
    if (rank != null) {
      sb.append(rank);
      sb.append(" ");
    }
    appendFullName(sb, false);
    if (id != null || parent != null) {
      sb.append(" [");
      if (id != null) {
        sb.append(id);
      }
      if (parent != null) {
        sb.append(" parent=");
        sb.append(parent);
      }
      sb.append("]");
    }
    return sb;
  }

  @Override
  public String toString() {
    return toStringBuilder().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SimpleName that = (SimpleName) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(name, that.name) &&
        Objects.equals(authorship, that.authorship) &&
        rank == that.rank &&
        code == that.code &&
        status == that.status &&
        Objects.equals(phrase, that.phrase) &&
        Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, authorship, rank, code, status, phrase, parent);
  }

  public int compareTo(SimpleName other) {
    return NATURAL_ORDER.compare(this, other);
  }
}
