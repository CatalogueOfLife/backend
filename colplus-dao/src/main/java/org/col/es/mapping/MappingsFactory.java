package org.col.es.mapping;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.col.es.EsModule;

import static org.col.es.mapping.ESDataType.KEYWORD;
import static org.col.es.mapping.MappingUtil.getClassForTypeArgument;
import static org.col.es.mapping.MultiField.AUTO_COMPLETE;
import static org.col.es.mapping.MultiField.DEFAULT;
import static org.col.es.mapping.MultiField.IGNORE_CASE;

public abstract class MappingsFactory {

  public static MappingsFactory usingFields() {
    return new FieldMappingsFactory();
  }

  public static MappingsFactory usingGetters() {
    return new GetterMappingsFactory();
  }

  private boolean mapEnumToInt = true;

  protected MappingsFactory() {}

   /**
   * Creates a document type mapping for the specified class name.
   *
   * @param className
   * @return
   */
  public Mappings getMapping(String className) {
    try {
      return getMapping(Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new MappingException("No such class: " + className);
    }
  }

  /**
   * Creates a document type mapping for the specified class.
   *
   * @param type
   * @return
   */
  public Mappings getMapping(Class<?> type) {
    Mappings mappings = new Mappings();
    addFieldsToDocument(mappings, type, newTree(new HashSet<>(), type));
    return mappings;
  }

  /**
   * Whether to map enums to Elasticsearch's integer datatype or to the keyword datatype. Default true. This is more or
   * less separate from how you <i>serialize</i> enums. Obviously when serializing enums to strings, you have no choice
   * but to use the keyword datatype. But when serializing enums to integers, you still have the choice of storing them as
   * strings or as integers. And there can be good reasons to store integers as strings (see Elasticsearch performance
   * tuning guide). Specifying the datatype mapping here saves you from having to decorate each and every enum in the data
   * model with the @MapToType annotation. You can still use the @MapToType to override the global behaviour. Note that in
   * {@link EsModule} we specify that we want enums to be serialized as integers, so we are <i>indeed</i> free to choose
   * between the keyword and integer datatype.
   */
  public boolean isMapEnumToInt() {
    return mapEnumToInt;
  }

  public void setMapEnumToInt(boolean mapEnumToInt) {
    this.mapEnumToInt = mapEnumToInt;
  }
  
  protected static SimpleField createSimpleField(AnnotatedElement fm, ESDataType datatype) {
    Boolean indexed = isIndexedField(fm, datatype);
    SimpleField sf;
    if (datatype == KEYWORD) {
      sf = new KeywordField(indexed);
      addMultiFields((KeywordField) sf, fm);
    } else if (datatype == ESDataType.DATE) {
      sf = new DateField(indexed);
    } else {
      sf = new SimpleField(datatype, indexed);
    }
    return sf;
  }


  protected abstract void addFieldsToDocument(ComplexField document, Class<?> type, HashSet<Class<?>> ancestors);

  private static void addMultiFields(KeywordField kf, AnnotatedElement fm) {
    Analyzers annotation = fm.getAnnotation(Analyzers.class);
    if (annotation != null && annotation.value().length != 0) {
      List<Analyzer> analyzers = Arrays.asList(annotation.value());
      for (Analyzer a : analyzers) {
        switch (a) {
          case IGNORE_CASE:
            kf.addMultiField(IGNORE_CASE);
            break;
          case DEFAULT:
            kf.addMultiField(DEFAULT);
            break;
          case AUTO_COMPLETE:
            kf.addMultiField(AUTO_COMPLETE);
            break;
          case KEYWORD:
          default:
            break;
        }
      }
    }
  }

  private static Boolean isIndexedField(AnnotatedElement fm, ESDataType esType) {
    if (esType == KEYWORD) {
      /*
       * String fields always have datatype KEYWORD. However, if they are not analyzed by the (no-op) KEYWORD analyzer, but
       * they __are__ analyzed using one or more other analyzers, the field will not be indexed as-is. It will only be indexed
       * using the other analyzers, and queries can only target the "multi fields" underneath the main field to access the
       * indexed values.
       */
      Analyzers annotation = fm.getAnnotation(Analyzers.class);
      if (annotation != null) {
        if (!Arrays.asList(annotation.value()).contains(Analyzer.KEYWORD)) {
          return Boolean.FALSE;
        }
      }
    }
    return fm.getAnnotation(NotIndexed.class) == null ? null : Boolean.FALSE;
  }

  protected static HashSet<Class<?>> newTree(HashSet<Class<?>> ancestors, Class<?> newType) {
    HashSet<Class<?>> set = new HashSet<>();
    set.addAll(ancestors);
    set.add(newType);
    return set;
  }

  protected Class<?> mapType(Class<?> cls, Type typeArg) {
    if (cls.isArray()) {
      return mapType(cls.getComponentType(), null);
    }
    if (Collection.class.isAssignableFrom(cls)) {
      return mapType(getClassForTypeArgument(typeArg), null);
    }
    if (cls.isEnum()) {
      return mapEnumToInt ? int.class : String.class;
    }
    return cls;
  }

  protected static boolean isA(Class<?> cls, Class<?> interfaceOrSuperClass) {
    return interfaceOrSuperClass.isAssignableFrom(cls);
  }

}
