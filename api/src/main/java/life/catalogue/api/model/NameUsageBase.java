package life.catalogue.api.model;

import life.catalogue.api.jackson.LabelPropertyFilter;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.NameFormatter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

/**
 *
 */
public abstract class NameUsageBase extends DatasetScopedEntity<String> implements NameUsage {
  public static final char EXTINCT_SYMBOL = '†';
  private static final Pattern SEC_YEAR = Pattern.compile("\\b[12][7890]\\d\\d\\b");
  private static final Pattern AUTHOR = Pattern.compile("^(?:[a-zA-Z]{1,3}[ '´`’.-]+){0,3}[\\p{L}-]{2,60}\\b");

  private Integer sectorKey;
  private Integer verbatimKey;
  @Nonnull
  @JsonFilter(LabelPropertyFilter.NAME)
  private Name name;
  @Nonnull
  private TaxonomicStatus status;
  @Nonnull
  private Origin origin;
  private String parentId;
  private String namePhrase;
  private String accordingTo; // read-only
  private String accordingToId;
  private List<Identifier> identifier;
  private URI link;
  private String remarks;
  /**
   * All bibliographic reference ids for the given name usage
   */
  private List<String> referenceIds = new ArrayList<>();

  public NameUsageBase() {
  }

  public NameUsageBase(Name n) {
    setName(n);
    setDatasetKey(n.getDatasetKey());
  }

  /**
   * Creates a shallow copy of the provided usage instance.
   */
  public NameUsageBase(NameUsageBase other) {
    super(other);
    this.sectorKey = other.sectorKey;
    this.verbatimKey = other.verbatimKey;
    this.name = other.name;
    this.status = other.status;
    this.origin = other.origin;
    this.parentId = other.parentId;
    this.namePhrase = other.namePhrase;
    this.accordingTo = other.accordingTo;
    this.accordingToId = other.accordingToId;
    this.identifier = other.identifier;
    this.link = other.link;
    this.remarks = other.remarks;
    this.referenceIds = other.referenceIds;
  }

  protected NameUsageBase(SimpleName sn) {
    setId(sn.getId());
    Name n = new Name(sn);
    setName(n);
    setStatus(sn.getStatus());
    setParentId(sn.getParent());
  }

  public abstract NameUsageBase copy();

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  @Override
  public String getLabel() {
    return getLabel(false);
  }

  @Override
  public String getLabelHtml() {
    return getLabel(true);
  }

  public String getLabel(boolean html) {
    return getLabelBuilder(html).toString();
  }

  @JsonIgnore
  public StringBuilder getLabelBuilder(boolean html) {
    return labelBuilder(name, null, status, namePhrase, accordingTo, html);
  }

  public static StringBuilder labelBuilder(Name name, Boolean extinct, TaxonomicStatus status, String namePhrase, String accordingTo, boolean html) {
    StringBuilder sb = new StringBuilder();
    if (Boolean.TRUE.equals(extinct)) {
      sb.append(EXTINCT_SYMBOL);
    }
    if (name != null) {
      String auct = null;
      if (status == TaxonomicStatus.MISAPPLIED && namePhrase == null && accordingTo == null) {
        // default for misapplied names: https://github.com/gbif/name-parser/issues/87
        auct = "auct. non";
      }
      name.appendNameLabel(sb, auct, html);
    } else {
      sb.append("UNNAMED");
    }
    if (namePhrase != null) {
      sb.append(" ");
      sb.append(namePhrase);
    }
    if (accordingTo != null && (namePhrase == null || namePhrase.length() < 6)) {
      sb.append(" ");
      if (html) {
        sb.append(NameFormatter.inItalics("sensu"));
      } else {
        sb.append("sensu");
      }
      sb.append(" ");
      sb.append(abbreviate(accordingTo));
    }
    return sb;
  }

  public static String abbreviate(String citation) {
    StringBuilder sb = new StringBuilder();
    if (citation != null) {
      Matcher m = AUTHOR.matcher(citation);
      if (m.find()) {
        sb.append(m.group());
      } else {
        if (citation.length() > 20) {
          sb.append(citation, 0, 20);
        } else {
          sb.append(citation);
        }
        sb.append("…");
      }

      m = SEC_YEAR.matcher(citation);
      if (m.find()) {
        sb.append(" ");
        sb.append(m.group());
      }
    }
    return sb.toString();
  }

  @Override
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    if (status != null && status.isBareName()) {
      throw new IllegalArgumentException("Usages cannot have a " + status + " status");
    }
    this.status = status;
  }
  
  @JsonIgnore
  public void setStatusIfNull(TaxonomicStatus status) {
    if (this.status == null) {
      this.status = Preconditions.checkNotNull(status);
    }
  }

  @Override
  public String getNamePhrase() {
    return namePhrase;
  }

  @Override
  public void setNamePhrase(String namePhrase) {
    this.namePhrase = namePhrase;
  }

  @JsonIgnore
  public boolean isProvisional() {
    return status == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
  }
  
  @Override
  public Origin getOrigin() {
    return origin;
  }
  
  @Override
  public void setOrigin(Origin origin) {
    this.origin = origin;
  }
  
  public String getParentId() {
    return parentId;
  }
  
  public void setParentId(String key) {
    this.parentId = key;
  }

  @Override
  public String getAccordingTo() {
    return accordingTo;
  }

  @Override
  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }

  @Override
  public String getAccordingToId() {
    return accordingToId;
  }
  
  public void setAccordingToId(String accordingToId) {
    this.accordingToId = accordingToId;
  }
  
  @Override
  public String getRemarks() {
    return remarks;
  }
  
  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public void addRemarks(String remarks) {
    if (!StringUtils.isBlank(remarks)) {
      this.remarks = this.remarks == null ? remarks.trim() : this.remarks + "; " + remarks.trim();
    }
  }

  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public List<String> getReferenceIds() {
    return referenceIds;
  }
  
  public void setReferenceIds(List<String> referenceIds) {
    this.referenceIds = referenceIds;
  }
  
  public SimpleNameLink toSimpleNameLink() {
    SimpleNameLink sn = SimpleNameLink.of(getId(), name.getScientificName(), name.getAuthorship(), name.getRank());
    sn.setStatus(status);
    sn.setCode(name.getCode());
    sn.setParent(parentId);
    return sn;
  }
  
  public URI getLink() {
    return link;
  }
  
  public void setLink(URI link) {
    this.link = link;
  }

  public List<Identifier> getIdentifier() {
    return identifier;
  }

  public void setIdentifier(List<Identifier> identifier) {
    this.identifier = identifier;
  }

  public void addIdentifier(String identifier) {
    if (!StringUtils.isBlank(identifier)) {
      addIdentifier(Identifier.parse(identifier));
    }
  }

  public void addIdentifier(Identifier id) {
    if (this.identifier == null) {
      this.identifier = new ArrayList<>();
    }
    this.identifier.add(id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameUsageBase)) return false;
    if (!super.equals(o)) return false;
    NameUsageBase that = (NameUsageBase) o;
    return Objects.equals(sectorKey, that.sectorKey) &&
      Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(name, that.name) &&
      status == that.status &&
      origin == that.origin &&
      Objects.equals(parentId, that.parentId) &&
      Objects.equals(namePhrase, that.namePhrase) &&
      Objects.equals(accordingToId, that.accordingToId) &&
      Objects.equals(identifier, that.identifier) &&
      Objects.equals(link, that.link) &&
      Objects.equals(remarks, that.remarks) &&
      Objects.equals(referenceIds, that.referenceIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, name, status, origin, parentId, namePhrase, accordingToId, identifier, link, remarks, referenceIds);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + name.getLabel() + " [" + getId() + "]}";
  }
}
