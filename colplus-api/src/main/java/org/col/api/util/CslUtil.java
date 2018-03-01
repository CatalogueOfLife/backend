package org.col.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.NotImplementedException;
import org.col.api.vocab.CSLVariable;

/**
 * Utilities to deal with CSL-JSON metadata for references.
 */
public class CslUtil {
  private final static String LITERAL = "literal";

  public static ObjectNode build(String authors, String year, String title, String source, String details) {
    ObjectNode csl = JsonNodeFactory.instance.objectNode();
    set(csl, CSLVariable.AUTHOR, authors);
    //set(csl, CSLVariable., year);
    set(csl, CSLVariable.TITLE, title);
    set(csl, CSLVariable.CONTAINER_TITLE, source);
    set(csl, CSLVariable.PAGE, details);
    return csl;
  }

  /**
   * Creates a complex name as a json object.
   * http://citeproc-js.readthedocs.io/en/latest/csl-json/markup.html#name-fields
   */
  public static ObjectNode name(String family, String given) {
    ObjectNode csl = JsonNodeFactory.instance.objectNode();
    throw new NotImplementedException("");
  }

  public static ObjectNode name(String literal) {
    return set(JsonNodeFactory.instance.objectNode(), LITERAL, literal);
  }

  public static Object value(ObjectNode csl, CSLVariable var) {
    if (csl.has(var.fieldName())) {
      JsonNode node = csl.get(var.fieldName());
      switch (var.type) {
        case NAME:
          return (ObjectNode) node;
        case NUMBER:
          if (node.isInt()) {
            return node.intValue();
          } else if (node.isLong()) {
            return node.longValue();
          } else if (node.isDouble()) {
            return node.doubleValue();
          } else if (node.isFloat()) {
            return node.floatValue();
          } else {
            return node.asInt();
          }
        case STRING:
        case DATE:
        default:
          return node.asText();
      }
    }
    return null;
  }

  public static Integer valueAsInt(ObjectNode csl, CSLVariable var) {
    throw new NotImplementedException("");
  }

  public static String valueAsString(ObjectNode csl, CSLVariable var) {
    throw new NotImplementedException("");
  }

  private static ObjectNode set(ObjectNode csl, String field, String value) {
    if (value == null) {
      csl.remove(field);
    } else {
      csl.put(field, value);
    }
    return csl;
  }

  public static ObjectNode set(ObjectNode csl, CSLVariable var, Object value) {
    if (value == null) {
      csl.remove(var.fieldName());
    } else {
      switch (var.type) {
        case NUMBER:
          if (value instanceof Integer) {
            csl.put(var.fieldName(), (int) value);
          } else if (value instanceof Long) {
            csl.put(var.fieldName(), (long) value);
          } else if (value instanceof Float) {
            csl.put(var.fieldName(), (float) value);
          } else if (value instanceof Double) {
            csl.put(var.fieldName(), (double) value);
          } else {
            csl.put(var.fieldName(), value.toString());
          }
        case DATE:
        case NAME:
        case STRING:
        default:
          csl.put(var.fieldName(), value.toString());
      }
    }
    return csl;
  }
}
