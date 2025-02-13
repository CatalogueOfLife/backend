package life.catalogue.common.text;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.common.date.FuzzyDate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CitationUtils {

  public static class DatasetWrapper {
    protected final Dataset d;

    public DatasetWrapper(Dataset dataset) {
      d = dataset;
    }

    public Integer getCreatedBy() {
      return d.getCreatedBy();
    }

    public Integer getModifiedBy() {
      return d.getModifiedBy();
    }

    public Integer getAttempt() {
      return d.getAttempt();
    }

    public Integer getContainerKey() {
      return d.getContainerKey();
    }

    public String getContainerTitle() {
      return d.getContainerTitle();
    }

    public List<Agent> getContainerCreator() {
      return d.getContainerCreator();
    }

    @JsonProperty("private")
    public boolean isPrivat() {
      return d.isPrivat();
    }

    public LocalDateTime getImported() {
      return d.getImported();
    }

    public LocalDateTime getDeleted() {
      return d.getDeleted();
    }

    public Map<String, String> getIdentifier() {
      return d.getIdentifier();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getCitation() {
      return d.getCitation();
    }

    @JsonIgnore
    public String getCitationText() {
      return d.getCitationText();
    }

    public Integer getKey() {
      return d.getKey();
    }

    public String getTitle() {
      return d.getTitle();
    }

    public String getDescription() {
      return d.getDescription();
    }

    public List<Agent> getCreator() {
      return d.getCreator();
    }

    public List<Agent> getEditor() {
      return d.getEditor();
    }

    public String getEditorsOrAuthors() {
      if (d.getEditor() != null && !d.getEditor().isEmpty()) {
        return getEditors();
      } else {
        return getAuthors();
      }
    }

    public String getAuthors() {
      return concat(d.getCreator());
    }

    public String getEditors() {
      if (!d.getEditor().isEmpty()) {
        String eds = concat(d.getEditor());
        if (d.getEditor().size() > 1) {
          return eds + " (eds.)";
        } else {
          return eds + " (ed.)";
        }
      }
      return null;
    }

    public String getContact() {
      return d.getContact().getName();
    }

    public Agent getPublisher() {
      return d.getPublisher();
    }

    public List<Agent> getContributor() {
      return d.getContributor();
    }

    public String getLicense() {
      return d.getLicense() == null ? null : d.getLicense().getTitle();
    }

    public String getVersion() {
      return d.getVersion();
    }

    public FuzzyDate getIssued() {
      return d.getIssued();
    }

    public URI getUrl() {
      return d.getUrl();
    }

    public URI getLogo() {
      return d.getLogo();
    }

    public List<Citation> getSource() {
      return d.getSource();
    }

    public String getIssn() {
      return d.getIssn();
    }

    public String getGeographicScope() {
      return d.getGeographicScope();
    }

    public String getTaxonomicScope() {
      return d.getTaxonomicScope();
    }

    public String getTemporalScope() {
      return d.getTemporalScope();
    }

    public FuzzyDate getReleased() {
      return d.getIssued();
    }

    public URI getWebsite() {
      return d.getUrl();
    }

    public String getAlias() {
      return d.getAlias();
    }

    public Integer getConfidence() {
      return d.getConfidence();
    }

    public Integer getCompleteness() {
      return d.getCompleteness();
    }

    public DatasetType getType() {
      return d.getType();
    }

    public Integer getSourceKey() {
      return d.getSourceKey();
    }

    public Integer getImportAttempt() {
      return d.getAttempt();
    }

    public DOI getDoi() {
      return d.getDoi();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getAliasOrTitle() {
      return d.getAliasOrTitle();
    }

    public DatasetOrigin getOrigin() {
      return d.getOrigin();
    }

    public UUID getGbifKey() {
      return d.getGbifKey();
    }

    public UUID getGbifPublisherKey() {
      return d.getGbifPublisherKey();
    }

    public Integer getSize() {
      return d.getSize();
    }

    public String getNotes() {
      return d.getNotes();
    }

    public LocalDateTime getCreated() {
      return d.getCreated();
    }

    public LocalDateTime getModified() {
      return d.getModified();
    }
  }

  public static class ReleaseWrapper extends DatasetWrapper {
    final DatasetWrapper base;
    final DatasetWrapper proj;

    public ReleaseWrapper(ReleaseWrapper other) {
      super(other.d);
      this.base = other.base;
      this.proj = other.proj;
    }

    public ReleaseWrapper(Dataset dataset, Dataset base, Dataset project) {
      super(dataset);
      this.base = new DatasetWrapper(base);
      this.proj = new DatasetWrapper(project);
    }

    public DatasetWrapper getBase() {
      return base;
    }

    public DatasetWrapper getProj() {
      return proj;
    }
  }

  public static String fromTemplate(DatasetWrapper dw, String template){
    if (template != null) {
      return SimpleTemplate.render(template, dw);
    }
    return null;
  }

  public static String fromTemplate(Dataset d, String template){
    if (template != null) {
      return SimpleTemplate.render(template, new DatasetWrapper(d));
    }
    return null;
  }

  static String concat(List<Agent> people) {
    return people == null ? null : people.stream().map(Agent::getName).collect(Collectors.joining(", "));
  }


}
