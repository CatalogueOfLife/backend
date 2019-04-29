package org.col.common.datapackage;

import java.util.Map;

/**
 *
 *
 */
public class Field {
  public static final String TYPE_STRING = "string";
  public static final String TYPE_INTEGER = "integer";
  public static final String TYPE_NUMBER = "number";
  public static final String TYPE_BOOLEAN = "boolean";
  public static final String TYPE_OBJECT = "object";
  public static final String TYPE_ARRAY = "array";
  public static final String TYPE_DATE = "date";
  public static final String TYPE_TIME = "time";
  public static final String TYPE_DATETIME = "datetime";
  public static final String TYPE_YEAR = "year";
  public static final String TYPE_YEARMONTH = "yearmonth";
  public static final String TYPE_DURATION = "duration";
  public static final String TYPE_GEOPOINT = "geopoint";
  public static final String TYPE_GEOJSON = "geojson";
  public static final String TYPE_ANY = "any";
  
  public static final String FORMAT_DEFAULT = "default";
  public static final String FORMAT_ARRAY = "array";
  public static final String FORMAT_OBJECT = "object";
  public static final String FORMAT_TOPOJSON = "topojson";
  public static final String FORMAT_EMAIL = "email";
  public static final String FORMAT_URI = "uri";
  
  public static final String CONSTRAINT_KEY_REQUIRED = "required";
  public static final String CONSTRAINT_KEY_UNIQUE = "unique";
  public static final String CONSTRAINT_KEY_MIN_LENGTH = "minLength";
  public static final String CONSTRAINT_KEY_MAX_LENGTH = "maxLength";
  public static final String CONSTRAINT_KEY_MINIMUM = "minimum";
  public static final String CONSTRAINT_KEY_MAXIMUM = "maximum";
  public static final String CONSTRAINT_KEY_PATTERN = "pattern";
  public static final String CONSTRAINT_KEY_ENUM = "enum";
  
  private String name;
  private String type;
  private String format = FORMAT_DEFAULT;
  private String title;
  private String description;
  private Map<String, Object> constraints;
  
  public Field(String name, String type) {
    this.name = name;
    this.type = type;
  }
  
  public Field(String name, String type, String format) {
    this.name = name;
    this.type = type;
    this.format = format;
  }
  
  public Field(String name, String type, String format, String title, String description) {
    this.name = name;
    this.type = type;
    this.format = format;
    this.title = title;
    this.description = description;
  }
  
  public Field(String name, String type, String format, String title, String description, Map constraints) {
    this.name = name;
    this.type = type;
    this.format = format;
    this.title = title;
    this.description = description;
    this.constraints = constraints;
  }
  
  public String getName() {
    return this.name;
  }
  
  public String getType() {
    return this.type;
  }
  
  public String getFormat() {
    return this.format;
  }
  
  public String getTitle() {
    return this.title;
  }
  
  public String getDescription() {
    return this.description;
  }
  
  public Map<String, Object> getConstraints() {
    return this.constraints;
  }
}