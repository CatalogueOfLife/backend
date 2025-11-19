package life.catalogue.importer.store.model;

import org.gbif.nameparser.api.Rank;



public class RankedUsage extends RankedName {
  public final String usageID;
  public final boolean synonym;

  public RankedUsage(String usageID, boolean synonym, String nameID, String name, String author, Rank rank) {
    super(nameID, name, author, rank);
    this.usageID = usageID;
    this.synonym = synonym;
  }

  public RankedUsage(UsageData data) {
    super(data.nameID, data.usage.getName().getScientificName(), data.usage.getName().getAuthorship(), data.usage.getRank());
    this.usageID = data.getId();
    this.synonym = data.isSynonym();
  }

  public String getUsageID() {
    return usageID;
  }

  public boolean isSynonym() {
    return synonym;
  }
}
