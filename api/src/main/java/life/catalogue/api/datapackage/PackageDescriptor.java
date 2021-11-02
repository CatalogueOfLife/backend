package life.catalogue.api.datapackage;

import java.util.List;
import java.util.Objects;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

public class PackageDescriptor {
  
  @QueryParam("title")
  private String title;
  
  @QueryParam("header")
  @DefaultValue("true")
  private boolean header = true;
  
  @QueryParam("base")
  private String base;
  
  @QueryParam("resource")
  private List<String> resources;
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }
  
  public boolean isHeader() {
    return header;
  }
  
  public void setHeader(boolean header) {
    this.header = header;
  }
  
  public String getBase() {
    return base;
  }
  
  public void setBase(String base) {
    this.base = base;
  }
  
  public List<String> getResources() {
    return resources;
  }
  
  public void setResources(List<String> resources) {
    this.resources = resources;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PackageDescriptor that = (PackageDescriptor) o;
    return header == that.header &&
        Objects.equals(title, that.title) &&
        Objects.equals(base, that.base) &&
        Objects.equals(resources, that.resources);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(title, header, base, resources);
  }
}
