package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.NameFormatter;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Comparator;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A small class representing a name usage with an id. It can act as a reference to a scientific name in a dataset.
 * It combines the source usage ID with the full scientific name in order to best deal with changing identifiers in sources.
 */
public class SimpleName implements Comparable<SimpleName>, NameUsageCore {
  private final static Comparator<String> nullSafeStringComparator = Comparator.nullsLast(String::compareTo);
  private final static Comparator<Enum> nullSafeEnumComparator = Comparator.nullsLast(Enum::compareTo);

  static final Comparator<SimpleName> NATURAL_ORDER =
      Comparator.comparing(SimpleName::getRank, nullSafeEnumComparator)
          .thenComparing(SimpleName::getName, nullSafeStringComparator)
          .thenComparing(SimpleName::getAuthorship, nullSafeStringComparator)
          .thenComparing(SimpleName::getPhrase, nullSafeStringComparator)
          .thenComparing(SimpleName::getStatus, nullSafeEnumComparator);

  private String id;
  @JsonIgnore // we include the dagger in the label already
  private boolean extinct;
  @NotNull
  private String name;
  private String authorship;
  private String phrase;
  @NotNull
  private Rank rank;
  private NomCode code;
  private TaxonomicStatus status;
  private String parent;

  public static SimpleName sn(String name) {
    return new SimpleName(null, name, Rank.UNRANKED);
  }
  public static SimpleName sn(String name, String authorship) {
    return new SimpleName(null, name, authorship, Rank.UNRANKED);
  }
  public static SimpleName sn(Rank rank, String name) {
    return new SimpleName(null, name, rank);
  }
  public static SimpleName sn(NomCode code, String name) {
    return new SimpleName(null, name, null, Rank.UNRANKED, code);
  }
  public static SimpleName sn(Rank rank, String name, String authorship) {
    return new SimpleName(null, name, authorship, rank);
  }
  public static SimpleName sn(Rank rank, NomCode code, String name, String authorship) {
    return new SimpleName(null, name, authorship, rank, code);
  }
  public static SimpleName sn(String id, Rank rank, String name, String authorship) {
    return new SimpleName(id, name, authorship, rank);
  }

  public SimpleName() {}

  public SimpleName(SimpleName other) {
    this.id = other.id;
    this.extinct = other.extinct;
    this.name = other.name;
    this.authorship = other.authorship;
    this.phrase = other.phrase;
    this.rank = other.rank;
    this.code = other.code;
    this.status = other.status;
    this.parent = other.parent;
  }

  public SimpleName(Name n) {
    this.id = n.getId();
    this.name = n.getScientificName();
    this.authorship = n.getAuthorship();
    this.rank = n.getRank();
    this.code = n.getCode();
  }

  public SimpleName(NameUsageBase u) {
    this.id = u.getId();
    this.name = u.getName().getScientificName();
    this.authorship = u.getName().getAuthorship();
    this.phrase = u.getNamePhrase();
    this.rank = u.getRank();
    this.code = u.getName().getCode();
    this.status = u.getStatus();
    this.parent = u.getParentId();
    if (u instanceof Taxon) {
      this.extinct = Boolean.TRUE.equals(((Taxon)u).isExtinct());
    }
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

  public SimpleName(String id, String name, String authorship, Rank rank, NomCode code) {
    this.id = id;
    this.name = name;
    this.authorship = authorship;
    this.rank = rank;
    this.code = code;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isExtinct() {
    return extinct;
  }

  public void setExtinct(boolean extinct) {
    this.extinct = extinct;
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

  @JsonIgnore
  public boolean hasAuthorship() {
    return !StringUtils.isBlank(authorship);
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

  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }

  @JsonIgnore
  public boolean isSynonym() {
    return status == null || status.isSynonym();
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public String getParent() {
    return parent;
  }

  @Override
  public String getParentId() {
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
    return strOrNull(appendFullName(new StringBuilder(), false));
  }

  public String getLabelHtml() {
    return strOrNull(appendFullName(new StringBuilder(), true));
  }


  private static String strOrNull(StringBuilder sb) {
    return sb==null || sb.length()<1 ? null : sb.toString();
  }

  private StringBuilder appendFullName(StringBuilder sb, boolean html) {
    if (extinct) {
      sb.append(NameUsageBase.EXTINCT_SYMBOL);
    }
    if (name != null) {
      sb.append(html ? NameFormatter.scientificNameHtml(name, rank) : name);
    }
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
      toStringAdditionalInfo(sb);
      sb.append("]");
    }
    return sb;
  }

  protected void toStringAdditionalInfo(StringBuilder sb) {
    // override to add more infos into the brackets
  }

  public DSID<String> toDSID(int datasetKey){
    return DSID.of(datasetKey, id);
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
