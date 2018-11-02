package org.col.es;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.col.es.query.EsSearchRequest;

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
  /**
   * Whether to store enums as ints or as strings. Storings as ints squeezes a bit more performance
   * out of ES and saves space, notably in the "payload" field of EsNameUsage. On the other hand, it
   * makes the index harder to read in Kibana.
   */
  public Boolean storeEnumAsInt = Boolean.TRUE;
  
  @JsonIgnore
  private ObjectMapper mapper;
  @JsonIgnore
  private ObjectReader reader;
  @JsonIgnore
  private ObjectWriter writer;
  @JsonIgnore
  private ObjectWriter queryWriter;

  /**
   * Returns a ObjectMapper for reading/writing from/to Elasticsearch.
   * 
   * @return
   */
  @JsonIgnore
  public ObjectMapper getMapper() {
    if (mapper == null) {
      mapper = new ObjectMapper();
      if (storeEnumAsInt) {
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
      }
      mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
      mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
      mapper.setSerializationInclusion(Include.NON_EMPTY);
      // Necessary because the SearchResponse class does not contain each and every field within an
      // Elasticsearch search response.
      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    return mapper;
  }

  /**
   * Returns a specialized ObjectReader used to read ES documents into EsNameUsage instances.
   */
  @JsonIgnore
  public ObjectReader getObjectReader() {
    if (reader == null) {
      try {
        reader = getMapper().readerFor(Class.forName(modelClass));
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
        writer = getMapper().writerFor(Class.forName(modelClass));
      } catch (ClassNotFoundException e) {
        throw new EsException(e);
      }
    }
    return writer;
  }

  /**
   * Returns a specialized ObjectWriter used to serialize Elasticsearch queries (which are actually
   * embodied by the EsSearchRequest class, not the Query interface).
   * 
   * @return
   */
  @JsonIgnore
  public ObjectWriter getQueryWriter() {
    if (queryWriter == null) {
      queryWriter = getMapper().writerFor(EsSearchRequest.class);
    }
    return queryWriter;
  }

}
