package life.catalogue.api.model;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ExportRequest {
  // filters
  private Integer datasetKey;
  private SimpleName root;
  private boolean synonyms = true;
  private Boolean extinct = null;
  private boolean bareNames = false;
  private Rank minRank;
  // output format
  private DataFormat format;
  private TabularFormat tabFormat = TabularFormat.TSV;
  private boolean excel;
  private boolean extended;
  private boolean force; // this makes sure we run a new export
  private boolean addClassification; // non public option to trigger a simple dwc tree download
  private boolean addTaxGroups; // non public option for simple downloads

  public ExportRequest() {
  }

  public ExportRequest(int datasetKey, DataFormat format) {
    this.datasetKey = datasetKey;
    this.format = format;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  public TabularFormat getTabFormat() {
    return tabFormat;
  }

  public void setTabFormat(TabularFormat tabFormat) {
    this.tabFormat = tabFormat;
  }

  public boolean isExtended() {
    return extended;
  }

  public void setExtended(boolean extended) {
    this.extended = extended;
  }

  @JsonIgnore
  public boolean isAddClassification() {
    return addClassification;
  }

  public void setAddClassification(boolean addClassification) {
    this.addClassification = addClassification;
  }

  @JsonIgnore
  public boolean isAddTaxGroups() {
    return addTaxGroups;
  }

  public void setAddTaxGroups(boolean addTaxGroups) {
    this.addTaxGroups = addTaxGroups;
  }

  public boolean isExcel() {
    return excel;
  }

  public void setExcel(boolean excel) {
    this.excel = excel;
  }

  public SimpleName getRoot() {
    return root;
  }

  public void setRoot(SimpleName root) {
    this.root = root;
  }

  @JsonIgnore
  public String getTaxonID() {
    return root == null ? null : root.getId();
  }

  public boolean isSynonyms() {
    return synonyms;
  }

  public void setSynonyms(boolean synonyms) {
    this.synonyms = synonyms;
  }

  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }

  public boolean isBareNames() {
    return bareNames;
  }

  public void setBareNames(boolean bareNames) {
    this.bareNames = bareNames;
  }

  public Rank getMinRank() {
    return minRank;
  }

  public void setMinRank(Rank minRank) {
    this.minRank = minRank;
  }

  public boolean isForce() {
    return force;
  }

  public void setForce(boolean force) {
    this.force = force;
  }

  /**
   * @return true if any filter has been used apart from the mandatory datasetKey & format
   */
  public boolean hasFilter() {
    return !synonyms || extinct!=null || bareNames || root!=null || minRank!=null;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ExportRequest that = (ExportRequest) o;
    return synonyms == that.synonyms && bareNames == that.bareNames && excel == that.excel && extended == that.extended && force == that.force && addClassification == that.addClassification && addTaxGroups == that.addTaxGroups && Objects.equals(datasetKey, that.datasetKey) && Objects.equals(root, that.root) && Objects.equals(extinct, that.extinct) && minRank == that.minRank && format == that.format && tabFormat == that.tabFormat;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, root, synonyms, extinct, bareNames, minRank, format, tabFormat, excel, extended, force, addClassification, addTaxGroups);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(format + " export of " + datasetKey);
    sb.append(" [excel=").append(excel)
      .append(", extended=").append(extended)
      .append(", synonyms=").append(synonyms)
      .append(", extinct=").append(extinct)
      .append(", bareNames=").append(bareNames);
    if (minRank != null) {
      sb.append(", minRank=").append(minRank);
    }
    if (root != null) {
      sb.append(", root=").append(root.getId());
    }
    sb.append("]");
    return sb.toString();
  }

  public TreeTraversalParameter toTreeTraversalParameter() {
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey);
    ttp.setTaxonID(getTaxonID());
    ttp.setSynonyms(isSynonyms());
    ttp.setLowestRank(getMinRank());
    return ttp;
  }
}
