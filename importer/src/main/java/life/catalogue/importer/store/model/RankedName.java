package life.catalogue.importer.store.model;

import org.gbif.nameparser.api.Rank;



public class RankedName {
  public final String nameID;
  public final String name;
  public final String author;
  public final String sensu;
  public final Rank rank;
  
  public RankedName(NameData nn) {
    this.nameID = nn.getId();
    this.name = nn.getName().getScientificName();
    this.author = nn.getName().getAuthorship();
    this.sensu = nn.pnu.getTaxonomicNote();
    this.rank = nn.getName().getRank();
  }
  
  public RankedName(String nameID, String name, String author, Rank rank) {
    this.nameID = nameID;
    this.name = name;
    this.author = author;
    this.sensu = null;
    this.rank = rank;
  }
  
  public String getNameID() {
    return nameID;
  }

  public String getNameWithAuthor() {
    return author == null ? name : name + " " + author;
  }
  
  @Override
  public String toString() {
    return toStringBuilder().toString();
  }
  
  public String toStringWithID() {
    StringBuilder sb = toStringBuilder();
    sb.append(" {");
    sb.append(getNameID());
    sb.append("}");
    return sb.toString();
  }
  
  public StringBuilder toStringBuilder() {
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    if (author != null) {
      sb.append(' ').append(author);
    }
    if (rank != null) {
      sb.append(" [").append(rank.name().toLowerCase()).append(']');
    }
    return sb;
  }
}
