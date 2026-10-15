package life.catalogue.db.type2;

import life.catalogue.api.model.TypifiedName;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Reads the names of a type material aggregated into a json array of {nameId, status} objects.
 */
public class JsonTypifiedNamesHandler extends JsonAbstractHandler<List<TypifiedName>> {

  public JsonTypifiedNamesHandler() {
    super(TypifiedName.class.getSimpleName(), new TypeReference<List<TypifiedName>>() {});
  }

}
