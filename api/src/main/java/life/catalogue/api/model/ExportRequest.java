package life.catalogue.api.model;

import life.catalogue.api.vocab.DataFormat;

import life.catalogue.api.vocab.TabularFormat;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ExportRequest {
  // filters
  private Integer datasetKey;
  private Integer sectorKey;
  private SimpleName root;
  private boolean synonyms = true;
  private Boolean extinct = null;
  private boolean bareNames = false;
  private Rank minRank;
  // output format
  private DataFormat format;
  private TabularFormat tabFormat = TabularFormat.TSV;
  private boolean excel;
  private boolean simple = true;
  private boolean force; // this makes sure we run a new export

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

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
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

  public boolean isSimple() {
    return simple;
  }

  public void setSimple(boolean simple) {
    this.simple = simple;
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
    if (this == o) return true;
    if (!(o instanceof ExportRequest)) return false;
    ExportRequest that = (ExportRequest) o;
    return excel == that.excel
           && simple == that.simple
           && synonyms == that.synonyms
           && bareNames == that.bareNames
           && force == that.force
           && Objects.equals(datasetKey, that.datasetKey)
           && Objects.equals(sectorKey, that.sectorKey)
           && format == that.format
           && tabFormat == that.tabFormat
           && Objects.equals(root, that.root)
           && Objects.equals(extinct, that.extinct)
           && minRank == that.minRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, sectorKey, format, tabFormat, excel, root, simple, synonyms, extinct, bareNames, minRank, force);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(format + " export of " + datasetKey);
    sb.append(" [excel=").append(excel)
      .append(", simple=").append(simple)
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
    ttp.setSectorKey(getSectorKey());
    ttp.setTaxonID(getTaxonID());
    ttp.setSynonyms(isSynonyms());
    ttp.setExtinct(getExtinct());
    ttp.setLowestRank(getMinRank());
    return ttp;
  }
}
