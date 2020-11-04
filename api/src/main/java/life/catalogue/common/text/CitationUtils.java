package life.catalogue.common.text;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Setting;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CitationUtils {

  static class DatasetWrapper {
    final ArchivedDataset d;

    public DatasetWrapper(ArchivedDataset dataset) {
      d = dataset;
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

    public String getEditorsOrAuthors() {
      if (d.getEditors() != null && !d.getEditors().isEmpty()) {
        return getEditors();
      } else {
        return getAuthors();
      }
    }

    public String getAuthors() {
      return concat(d.getAuthors());
    }

    public String getEditors() {
      if (!d.getEditors().isEmpty()) {
        String eds = concat(d.getEditors());
        if (d.getEditors().size() > 1) {
          return eds + " (eds.)";
        } else {
          return eds + " (ed.)";
        }
      }
      return null;
    }

    public String getOrganisations() {
      return getOrganisations() == null ? null : String.join(", ", getOrganisations());
    }

    public String getContact() {
      return d.getContact().getName();
    }

    public String getLicense() {
      return d.getLicense() == null ? null : d.getLicense().title;
    }

    public String getVersion() {
      return d.getVersion();
    }

    public String getGeographicScope() {
      return d.getGeographicScope();
    }

    public LocalDate getReleased() {
      return d.getReleased();
    }

    public String getCitation() {
      return d.getCitation();
    }

    public URI getWebsite() {
      return d.getWebsite();
    }

    public String getAlias() {
      return d.getAlias();
    }

    public String getGroup() {
      return d.getGroup();
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
      return d.getImportAttempt();
    }

    public String getAliasOrTitle() {
      return d.getAliasOrTitle();
    }

    public LocalDateTime getCreated() {
      return d.getCreated();
    }

    public LocalDateTime getModified() {
      return d.getModified();
    }
  }

  static class ProjectWrapper extends DatasetWrapper {
    final DatasetWrapper project;

    public ProjectWrapper(ArchivedDataset dataset, ArchivedDataset project) {
      super(dataset);
      this.project = new DatasetWrapper(project);
    }

    public DatasetWrapper getProject() {
      return project;
    }
  }

  public static String fromTemplate(Dataset d, DatasetSettings ds, Setting setting, String defaultTemplate){
    String tmpl = defaultTemplate;
    if (ds.has(setting)) {
      tmpl = ds.getString(setting);
    }
    return fromTemplate(d, tmpl);
  }

  public static String fromTemplate(Dataset d, String template){
    if (template != null) {
      return SimpleTemplate.render(template, new DatasetWrapper(d));
    }
    return null;
  }

  public static String fromTemplate(ArchivedDataset d, ArchivedDataset project, String template){
    if (template != null) {
      return SimpleTemplate.render(template, new ProjectWrapper(d, project));
    }
    return null;
  }

  public static String concat(List<Person> people) {
    return people == null ? null : people.stream().map(Person::getName).collect(Collectors.joining(", "));
  }

  public static String concatEditors(List<Person> editors) {
    String x = concat(editors);
    if (x != null) {
      if (editors.size() > 1) {
        return x + " (eds.)";
      } else {
        return x + " (ed.)";
      }
    }
    return x;
  }

  @VisibleForTesting
  protected static String buildCitation(Dataset d){
    // ${d.authorsAndEditors?join(", ")}, eds. (${d.released.format('yyyy')}). ${d.title}, ${d.released.format('yyyy-MM-dd')}. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.
    StringBuilder sb = new StringBuilder();
    boolean isEditors = false;
    List<Person> people = Collections.emptyList();
    if (d.getEditors() != null && !d.getEditors().isEmpty()) {
      people = d.getEditors();
      isEditors = true;
    } else if (d.getAuthors() != null && !d.getAuthors().isEmpty()) {
      people = d.getAuthors();
    }
    for (Person au : people) {
      if (sb.length() > 1) {
        sb.append(", ");
      }
      sb.append(au.getName());
    }
    if (isEditors) {
      sb.append(" (ed");
      if (people.size()>1) {
        sb.append("s");
      }
      sb.append(".)");
    }
    sb.append(" (")
      .append(d.getReleased().getYear())
      .append("). ")
      .append(d.getTitle())
      .append(", ")
      .append(d.getReleased().toString())
      .append(".");
    return sb.toString();
  }
}
