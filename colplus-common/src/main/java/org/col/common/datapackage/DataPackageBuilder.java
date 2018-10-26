package org.col.common.datapackage;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.col.api.datapackage.ColTerm;
import org.col.api.datapackage.PackageDescriptor;
import org.col.api.jackson.ApiModule;
import org.col.common.text.UnicodeUtils;

public class DataPackageBuilder {
  // only non string data types here
  private static final Map<ColTerm, String> dataTypes = ImmutableMap.of(
  
  );
  private static final Map<ColTerm, Class<Enum<?>>> enums = ImmutableMap.of(
  
  );
  private static final Set<ColTerm> required = ImmutableSet.of(ColTerm.ID, ColTerm.scientificName);
  
  public DataPackage fullPackage(String baseURL) {
    PackageDescriptor pd = new PackageDescriptor();
    pd.setBase(baseURL);
    pd.setResources(ColTerm.RESOURCES.keySet().stream()
        .map(t -> t.name().toLowerCase() + ".tsv")
        .collect(Collectors.toList())
    );
    return build(pd);
  }

  private String titleToName(String t) {
    return UnicodeUtils.ascii(t).replaceAll("\\s+", "-");
  }
  
  public DataPackage build(PackageDescriptor pd) {
    DataPackage p = new DataPackage();
    p.setTitle(pd.getTitle());
    p.setName(titleToName(pd.getTitle()));
    
    for (String res : pd.getResources()) {
      Resource r = new Resource();
      r.setUrl(resourceUrl(pd.getBase(), res));
      if (!res.toLowerCase().endsWith("csv")) {
        r.setDialect(Dialect.TSV);
      }
      r.setSchema(buildSchema(res));
      p.getResources().add(r);
    }
    return p;
  }
  
  private static String resourceUrl(String baseURL, String resource) {
    return resource;
  }
  
  private Schema buildSchema(String resource) {
    ColTerm rowType = ColTerm.find(FilenameUtils.getBaseName(resource), true);
    if (rowType == null) {
      throw new UnknownEntityException("Unknown entity " + resource);
    
    } else if (!rowType.isClass()) {
      throw new UnknownEntityException(resource + " is not a class term");
    }
    
    Schema s = new Schema();
    s.setRowType(rowType);
    
    for (ColTerm t : ColTerm.RESOURCES.get(rowType)) {
      String type = dataTypes.getOrDefault(t, Field.FIELD_TYPE_STRING);
      String format = "default";
      Map<String, Object> constraints = new HashMap<>();
      if (enums.containsKey(t)) {
        List<String> values = Arrays.stream(enums.get(t).getEnumConstants())
            .map(ApiModule::enumValueName)
            .collect(Collectors.toList());
        constraints.put(Field.CONSTRAINT_KEY_ENUM, values);
      }
      if (required.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_REQUIRED, true);
      }
      if (ColTerm.ID == t) {
        constraints.put(Field.CONSTRAINT_KEY_UNIQUE, true);
      }
      s.addField(new Field(t.simpleName(), type, format, null, null, constraints));
    }
    return s;
  }
}
