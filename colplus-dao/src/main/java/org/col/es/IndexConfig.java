package org.col.es;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class IndexConfig {

  /**
   * The model class corresponding to the type.
   */
  public String modelClass;
  public int numShards = 1;
  public int numReplicas = 0;
  /**
   * Batch size for bulk requests
   */
  public int batchSize = 1000;
  
  @JsonIgnore
  private ObjectReader reader;
  @JsonIgnore
  private ObjectWriter writer;
  

  /**
   * Returns a specialized ObjectReader used to read ES documents into EsNameUsage instances.
   */
  @JsonIgnore
  public ObjectReader getObjectReader() {
    if (reader == null) {
      try {
        reader = EsModule.MAPPER.readerFor(Class.forName(modelClass));
      } catch (ClassNotFoundException e) {
        throw new EsException(e);
      }
    }
    return reader;
  }

  /**
   * Returns a specialized ObjectWriter used to serialize EsNameUsage instances.
   * 
   * @return
   */
  @JsonIgnore
  public ObjectWriter getObjectWriter() {
    if (writer == null) {
      try {
        writer = EsModule.MAPPER.writerFor(Class.forName(modelClass));
      } catch (ClassNotFoundException e) {
        throw new EsException(e);
      }
    }
    return writer;
  }
  
}
