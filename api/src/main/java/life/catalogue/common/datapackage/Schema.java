package life.catalogue.common.datapackage;

import life.catalogue.coldp.ColdpTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 *
 *
 */
public class Schema {

  private static final String VERSION = "1.1";
  private static final String IDENTIFIER = "http://rs.gbif.org/data-packages/coldp/";
  private String description;
  @JsonIgnore
  private ColdpTerm rowType;
  private List<Field> fields = new ArrayList<>();
  private String primaryKey;
  private List<ForeignKey> foreignKeys = new ArrayList<>();

  public String getIdentifier() {
    return IDENTIFIER;
  }

  public String getUrl() {
    return IDENTIFIER + VERSION + "/table-schemas/" + rowType.simpleName().toLowerCase() + ".json";
  }


  public String getName() {
    return rowType.simpleName().toLowerCase();
  }

  public String getTitle() {
    return rowType.simpleName();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ColdpTerm getRowType() {
    return rowType;
  }
  
  public void setRowType(ColdpTerm rowType) {
    this.rowType = rowType;
  }
  
  public List<Field> getFields() {
    return fields;
  }
  
  public void setFields(List<Field> fields) {
    this.fields = fields;
  }
  
  public String getPrimaryKey() {
    return primaryKey;
  }
  
  public void setPrimaryKey(String primaryKey) {
    this.primaryKey = primaryKey;
  }
  
  public List<ForeignKey> getForeignKeys() {
    return foreignKeys;
  }
  
  public void setForeignKeys(List<ForeignKey> foreignKeys) {
    this.foreignKeys = foreignKeys;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Schema)) return false;
    Schema schema = (Schema) o;
    return Objects.equals(description, schema.description) && rowType == schema.rowType && Objects.equals(fields, schema.fields) && Objects.equals(primaryKey, schema.primaryKey) && Objects.equals(foreignKeys, schema.foreignKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, rowType, fields, primaryKey, foreignKeys);
  }
}