package org.col.common.datapackage;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.col.api.datapackage.ColdpTerm;
import org.col.api.datapackage.PackageDescriptor;
import org.col.api.jackson.PermissiveEnumSerde;
import org.col.common.text.StringUtils;
import org.col.api.vocab.*;
import org.col.common.text.UnicodeUtils;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

public class DataPackageBuilder {
  private static final String MONOMIAL_PATTERN = "[A-ZÏËÖÜÄÉÈČÁÀÆŒ](?:\\.|[a-zïëöüäåéèčáàæœ]+)(?:-[A-ZÏËÖÜÄÉÈČÁÀÆŒ]?[a-zïëöüäåéèčáàæœ]+)?";
  
  // only non string data types here
  private static final Map<ColdpTerm, String> dataTypes = ImmutableMap.<ColdpTerm, String>builder()
      .put(ColdpTerm.year, Field.TYPE_YEAR)
      .put(ColdpTerm.accordingToDate, Field.TYPE_DATE)
      .put(ColdpTerm.created, Field.TYPE_DATETIME)
      .put(ColdpTerm.fossil, Field.TYPE_BOOLEAN)
      .put(ColdpTerm.recent, Field.TYPE_BOOLEAN)
      .build();
  
  private static final Map<ColdpTerm, String> dataFormats = ImmutableMap.<ColdpTerm, String>builder()
      .put(ColdpTerm.link, Field.FORMAT_URI)
      .build();
  
  private static final Set<ColdpTerm> monomials = ImmutableSet.of(
      ColdpTerm.kingdom, ColdpTerm.phylum, ColdpTerm.class_, ColdpTerm.order, ColdpTerm.family,
      ColdpTerm.subphylum, ColdpTerm.subclass, ColdpTerm.suborder, ColdpTerm.subfamily,
      ColdpTerm.superfamily
  );

  private static final Map<ColdpTerm, Class<? extends Enum>> enums = ImmutableMap.<ColdpTerm, Class<? extends Enum>>builder()
      .put(ColdpTerm.status, TaxonomicStatus.class)
      .put(ColdpTerm.type, NomRelType.class)
      .put(ColdpTerm.code, NomCode.class)
      .put(ColdpTerm.country, Country.class)
      .put(ColdpTerm.language, Language.class)
      .put(ColdpTerm.format, DataFormat.class)
      .put(ColdpTerm.gazetteer, Gazetteer.class)
      .put(ColdpTerm.rank, Rank.class)
      .put(ColdpTerm.lifezone, Lifezone.class)
      .build();
  
  private static final Map<ColdpTerm, ForeignKey> foreignKeys = ImmutableMap.<ColdpTerm, ForeignKey>builder()
      .put(ColdpTerm.referenceID, new ForeignKey(ColdpTerm.referenceID, ColdpTerm.Reference, ColdpTerm.ID))
      .put(ColdpTerm.nameID, new ForeignKey(ColdpTerm.nameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.relatedNameID, new ForeignKey(ColdpTerm.relatedNameID, ColdpTerm.Name, ColdpTerm.ID))
      .put(ColdpTerm.taxonID, new ForeignKey(ColdpTerm.taxonID, ColdpTerm.Taxon, ColdpTerm.ID))
      .put(ColdpTerm.parentID, new ForeignKey(ColdpTerm.parentID, ColdpTerm.Taxon, ColdpTerm.ID))
      .build();

  private static final Set<ColdpTerm> required = ImmutableSet.of(ColdpTerm.ID, ColdpTerm.scientificName);
  
  private String titleToName(String t) {
    if (StringUtils.hasContent(t)) {
      return UnicodeUtils.ascii(t).replaceAll("\\s+", "-");
    }
    return null;
  }
  
  public DataPackage build(PackageDescriptor pd) {
    DataPackage p = new DataPackage();
    p.setTitle(pd.getTitle());
    p.setName(titleToName(pd.getTitle()));
    
    if (pd.getResources() == null || pd.getResources().isEmpty()) {
      // use all as default!
      pd.setResources(ColdpTerm.RESOURCES.keySet().stream()
          .map(t -> t.name().toLowerCase() + ".tsv")
          .collect(Collectors.toList())
      );
    }
    
    for (String res : pd.getResources()) {
      Resource r = new Resource();
      r.setPath(resourceUrl(pd.getBase(), res));
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
    ColdpTerm rowType = ColdpTerm.find(FilenameUtils.getBaseName(resource), true);
    if (rowType == null) {
      throw new UnknownEntityException("Unknown entity " + resource);

    } else if (!rowType.isClass()) {
      throw new UnknownEntityException(resource + " is not a class term");
    }
    
    Schema s = new Schema();
    s.setRowType(rowType);
    
    for (ColdpTerm t : ColdpTerm.RESOURCES.get(rowType)) {
      String type = dataTypes.getOrDefault(t, Field.TYPE_STRING);
      String format = dataFormats.getOrDefault(t, Field.FORMAT_DEFAULT);
      Map<String, Object> constraints = new HashMap<>();
      if (enums.containsKey(t)) {
        constraints.put(Field.CONSTRAINT_KEY_ENUM, enumValues(rowType, t));
      }
      if (required.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_REQUIRED, true);
      }
      if (ColdpTerm.ID == t) {
        s.setPrimaryKey(t.simpleName());
        //constraints.put(Field.CONSTRAINT_KEY_UNIQUE, true);
      }
      if (foreignKeys.containsKey(t)) {
        s.getForeignKeys().add(foreignKeys.get(t));
      }
      if (monomials.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_PATTERN, MONOMIAL_PATTERN);
      }
      s.getFields().add(new Field(t.simpleName(), type, format, null, null, constraints));
    }
    return s;
  }
  
  private List<String> enumValues(ColdpTerm rowType, ColdpTerm t) {
    // special cases
    switch (t) {
      case country:
        return enumValues(Country.class, Country::getIso2LetterCode);
      case language:
        return enumValues(Language.class, Language::getIso3LetterCode);
    }
    
    Class<? extends Enum> enumClass;
    if (t == ColdpTerm.status && rowType == ColdpTerm.Name) {
      enumClass = NomStatus.class;
      
    } else if (t == ColdpTerm.type && rowType == ColdpTerm.Media) {
      enumClass = MediaType.class;

    } else {
      enumClass = enums.get(t);
    }

    return enumValues(enumClass, PermissiveEnumSerde::enumValueName);
  }
  
  private static <T extends Enum> List<String> enumValues(Class<T> eClass, Function<T, String> mapper) {
    return Arrays.stream(eClass.getEnumConstants())
        .map(mapper)
        .collect(Collectors.toList());
  }
}
