package org.col.es.query;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.EsException;
import org.col.es.EsModule;

abstract class AbstractQuery implements Query {

  public String toString() {
    try {
      return EsModule.QUERY_WRITER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}
