package org.col.common.datapackage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.datapackage.ColTerm;


/**
 *
 *
 */
public class Schema {
  private static final int JSON_INDENT_FACTOR = 4;
  public static final String JSON_KEY_FIELDS = "fields";
  public static final String JSON_KEY_PRIMARY_KEY = "primaryKey";
  public static final String JSON_KEY_FOREIGN_KEYS = "foreignKeys";
  
  @JsonIgnore
  private ColTerm rowType;
  private List<Field> fields = new ArrayList();
  private Object primaryKey = null;
  private List<ForeignKey> foreignKeys = new ArrayList();
  
  public ColTerm getRowType() {
    return rowType;
  }
  
  public void setRowType(ColTerm rowType) {
    this.rowType = rowType;
  }
  
  public void addField(Field field){
    this.fields.add(field);
  }
  
  
  public List<Field> getFields(){
    return this.fields;
  }
  
  public Field getField(String name){
    Iterator<Field> iter = this.fields.iterator();
    while(iter.hasNext()){
      Field field = iter.next();
      if(field.getName().equalsIgnoreCase(name)){
        return field;
      }
    }
    return null;
  }
  
  public boolean hasField(String name){
    Iterator<Field> iter = this.fields.iterator();
    while(iter.hasNext()){
      Field field = iter.next();
      if(field.getName().equalsIgnoreCase(name)){
        return true;
      }
    }
    
    return false;
  }
  
  public boolean hasFields(){
    return !this.getFields().isEmpty();
  }
  
  
  
  /**
   * Set single primary key with the option of validation.
   * @param key
   */
  public void setPrimaryKey(String key) {
    this.primaryKey = key;
  }
  
  /**
   * Set composite primary key with the option of validation.
   * @param compositeKey
   */
  public void setPrimaryKey(String[] compositeKey) {
    this.primaryKey = compositeKey;
  }
  
  public <Any> Any getPrimaryKey(){
    return (Any)this.primaryKey;
  }
  
  public List<ForeignKey> getForeignKeys(){
    return this.foreignKeys;
  }
  
  public void addForeignKey(ForeignKey foreignKey){
    this.foreignKeys.add(foreignKey);
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