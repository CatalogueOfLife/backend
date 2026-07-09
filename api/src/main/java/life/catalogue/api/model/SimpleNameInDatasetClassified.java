package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxGroup;

import java.util.List;
import java.util.Objects;

public class SimpleNameInDatasetClassified extends SimpleNameInDataset {
  private List<SimpleName> classification;
  private SimpleName accepted;
  private TaxGroup group;
  private String datasetTitle;
  private String datasetAlias;

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public SimpleName getAccepted() {
    return accepted;
  }

  public void setAccepted(SimpleName accepted) {
    this.accepted = accepted;
  }

  @Override
  public TaxGroup getGroup() {
    return group;
  }

  @Override
  public void setGroup(TaxGroup group) {
    this.group = group;
  }

  public String getDatasetTitle() {
    return datasetTitle;
  }

  public void setDatasetTitle(String datasetTitle) {
    this.datasetTitle = datasetTitle;
  }

  public String getDatasetAlias() {
    return datasetAlias;
  }

  public void setDatasetAlias(String datasetAlias) {
    this.datasetAlias = datasetAlias;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleNameInDatasetClassified that)) return false;
    if (!super.equals(o)) return false;

    return Objects.equals(classification, that.classification) &&
        group == that.group &&
        Objects.equals(datasetTitle, that.datasetTitle) &&
        Objects.equals(datasetAlias, that.datasetAlias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classification, group, datasetTitle, datasetAlias);
  }
}
