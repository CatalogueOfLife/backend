package life.catalogue.common.datapackage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.datapackage.ColdpTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 *
 *
 */
public class Schema {
  
  @JsonIgnore
  private ColdpTerm rowType;
  private List<Field> fields = new ArrayList<>();
  private String primaryKey;
  private List<ForeignKey> foreignKeys = new ArrayList<>();
  
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
    if (o == null || getClass() != o.getClass()) return false;
    Schema schema = (Schema) o;
    return rowType == schema.rowType &&
        Objects.equals(fields, schema.fields) &&
        Objects.equals(primaryKey, schema.primaryKey) &&
        Objects.equals(foreignKeys, schema.foreignKeys);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(rowType, fields, primaryKey, foreignKeys);
  }
}