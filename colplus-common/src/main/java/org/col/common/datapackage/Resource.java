package org.col.common.datapackage;

import java.util.Objects;

public class Resource {
  private String encoding = "UTF-8";
  private final String format = "csv";
  private String name;
  private String url;
  private Dialect dialect;
  private Schema schema;
  private final String profile = "tabular-data-resource";
  
  public String getEncoding() {
    return encoding;
  }
  
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }
  
  public String getFormat() {
    return format;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  public void setDialect(Dialect dialect) {
    this.dialect = dialect;
  }
  
  public Dialect getDialect() {
    return dialect;
  }
  
  public String getUrl() {
    return url;
  }
  
  public void setUrl(String url) {
    this.url = url;
  }
  
  public Schema getSchema() {
    return schema;
  }
  
  public void setSchema(Schema schema) {
    this.schema = schema;
    if (schema != null) {
      this.name = schema.getRowType().simpleName();
    }
  }
  
  public String getProfile() {
    return profile;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Resource resource = (Resource) o;
    return Objects.equals(encoding, resource.encoding) &&
        Objects.equals(format, resource.format) &&
        Objects.equals(name, resource.name) &&
        Objects.equals(url, resource.url) &&
        Objects.equals(dialect, resource.dialect) &&
        Objects.equals(schema, resource.schema) &&
        Objects.equals(profile, resource.profile);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(encoding, format, name, url, dialect, schema, profile);
  }
}
