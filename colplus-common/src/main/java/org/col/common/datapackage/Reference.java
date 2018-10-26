package org.col.common.datapackage;
import java.net.URL;

/**
 *
 *
 */
public class Reference {
  private static final String JSON_KEY_DATAPACKAGE = "datapackage";
  private static final String JSON_KEY_RESOURCE = "resource";
  private static final String JSON_KEY_FIELDS = "fields";
  
  private URL datapackage = null;
  private String resource = null;
  private Object fields = null;
  
  
  public URL getDatapackage(){
    return this.datapackage;
  }
  
  public void setDatapackage(URL datapackage){
    this.datapackage = datapackage;
  }
  
  public String getResource(){
    return this.resource;
  }
  
  public void setResource(String resource){
    this.resource = resource;
  }
  
  public <Any> Any getFields(){
    return (Any)this.fields;
  }
  
  public void setFields(Object fields){
    this.fields = fields;
  }
  
}