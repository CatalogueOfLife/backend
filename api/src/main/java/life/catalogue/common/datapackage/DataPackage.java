package life.catalogue.common.datapackage;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.Lists;

public class DataPackage {
  
  private String title;
  private String name;
  private final String profile = "tabular-data-package";
  private List<Resource> resources = Lists.newArrayList();
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getProfile() {
    return profile;
  }
  
  public List<Resource> getResources() {
    return resources;
  }
  
  public void setResources(List<Resource> resources) {
    this.resources = resources;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataPackage that = (DataPackage) o;
    return Objects.equals(title, that.title) &&
        Objects.equals(name, that.name) &&
        Objects.equals(profile, that.profile) &&
        Objects.equals(resources, that.resources);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(title, name, profile, resources);
  }
}
