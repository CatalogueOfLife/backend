package org.col.common.datapackage;

import java.util.Map;

/**
 *
 *
 */
public class Field {
  public static final String FIELD_TYPE_STRING = "string";
  public static final String FIELD_TYPE_INTEGER = "integer";
  public static final String FIELD_TYPE_NUMBER = "number";
  public static final String FIELD_TYPE_BOOLEAN = "boolean";
  public static final String FIELD_TYPE_OBJECT = "object";
  public static final String FIELD_TYPE_ARRAY = "array";
  public static final String FIELD_TYPE_DATE = "date";
  public static final String FIELD_TYPE_TIME = "time";
  public static final String FIELD_TYPE_DATETIME = "datetime";
  public static final String FIELD_TYPE_YEAR = "year";
  public static final String FIELD_TYPE_YEARMONTH = "yearmonth";
  public static final String FIELD_TYPE_DURATION = "duration";
  public static final String FIELD_TYPE_GEOPOINT = "geopoint";
  public static final String FIELD_TYPE_GEOJSON = "geojson";
  public static final String FIELD_TYPE_ANY = "any";
  
  public static final String FIELD_FORMAT_DEFAULT = "default";
  public static final String FIELD_FORMAT_ARRAY = "array";
  public static final String FIELD_FORMAT_OBJECT = "object";
  public static final String FIELD_FORMAT_TOPOJSON = "topojson";
  
  public static final String CONSTRAINT_KEY_REQUIRED = "required";
  public static final String CONSTRAINT_KEY_UNIQUE = "unique";
  public static final String CONSTRAINT_KEY_MIN_LENGTH = "minLength";
  public static final String CONSTRAINT_KEY_MAX_LENGTH = "maxLength";
  public static final String CONSTRAINT_KEY_MINIMUM = "minimum";
  public static final String CONSTRAINT_KEY_MAXIMUM = "maximum";
  public static final String CONSTRAINT_KEY_PATTERN = "pattern";
  public static final String CONSTRAINT_KEY_ENUM = "enum";
  
  public static final String JSON_KEY_NAME = "name";
  public static final String JSON_KEY_TYPE = "type";
  public static final String JSON_KEY_FORMAT = "format";
  public static final String JSON_KEY_TITLE = "title";
  public static final String JSON_KEY_DESCRIPTION = "description";
  public static final String JSON_KEY_CONSTRAINTS = "constraints";
  
  private String name = "";
  private String type = "";
  private String format = FIELD_FORMAT_DEFAULT;
  private String title = "";
  private String description = "";
  private Map<String, Object> constraints = null;
  
  public Field(String name, String type){
    this.name = name;
    this.type = type;
  }
  
  public Field(String name, String type, String format){
    this.name = name;
    this.type = type;
    this.format = format;
  }
  
  public Field(String name, String type, String format, String title, String description){
    this.name = name;
    this.type = type;
    this.format = format;
    this.title = title;
    this.description = description;
  }
  
  public Field(String name, String type, String format, String title, String description, Map constraints){
    this.name = name;
    this.type = type;
    this.format = format;
    this.title = title;
    this.description = description;
    this.constraints = constraints;
  }
  
  public String getName(){
    return this.name;
  }
  
  public String getType(){
    return this.type;
  }
  
  public String getFormat(){
    return this.format;
  }
  
  public String getTitle(){
    return this.title;
  }
  
  public String getDescription(){
    return this.description;
  }
  
  public Map<String, Object> getConstraints(){
    return this.constraints;
  }
}