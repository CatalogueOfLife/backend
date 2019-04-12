package org.col.common.datapackage;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.col.api.datapackage.ColTerm;
import org.col.api.datapackage.PackageDescriptor;
import org.col.api.jackson.PermissiveEnumSerde;
import org.col.api.vocab.*;
import org.col.common.text.StringUtils;
import org.col.common.text.UnicodeUtils;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

public class DataPackageBuilder {
  private static final String MONOMIAL_PATTERN = "[A-ZÏËÖÜÄÉÈČÁÀÆŒ](?:\\.|[a-zïëöüäåéèčáàæœ]+)(?:-[A-ZÏËÖÜÄÉÈČÁÀÆŒ]?[a-zïëöüäåéèčáàæœ]+)?";
  
  // only non string data types here
  private static final Map<ColTerm, String> dataTypes = ImmutableMap.<ColTerm, String>builder()
      .put(ColTerm.year, Field.TYPE_YEAR)
      .put(ColTerm.accordingToDate, Field.TYPE_DATE)
      .put(ColTerm.created, Field.TYPE_DATETIME)
      .put(ColTerm.fossil, Field.TYPE_BOOLEAN)
      .put(ColTerm.recent, Field.TYPE_BOOLEAN)
      .build();
  
  private static final Map<ColTerm, String> dataFormats = ImmutableMap.<ColTerm, String>builder()
      .put(ColTerm.link, Field.FORMAT_URI)
      .build();
  
  private static final Set<ColTerm> monomials = ImmutableSet.of(
      ColTerm.kingdom, ColTerm.phylum, ColTerm.class_, ColTerm.order, ColTerm.family,
      ColTerm.subphylum, ColTerm.subclass, ColTerm.suborder, ColTerm.subfamily,
      ColTerm.superfamily
  );

  private static final Map<ColTerm, Class<? extends Enum>> enums = ImmutableMap.<ColTerm, Class<? extends Enum>>builder()
      .put(ColTerm.status, TaxonomicStatus.class)
      .put(ColTerm.type, NomRelType.class)
      .put(ColTerm.code, NomCode.class)
      .put(ColTerm.country, Country.class)
      .put(ColTerm.language, Language.class)
      .put(ColTerm.format, DataFormat.class)
      .put(ColTerm.gazetteer, Gazetteer.class)
      .put(ColTerm.rank, Rank.class)
      .put(ColTerm.lifezone, Lifezone.class)
      .build();
  
  private static final Map<ColTerm, ForeignKey> foreignKeys = ImmutableMap.<ColTerm, ForeignKey>builder()
      .put(ColTerm.referenceID, new ForeignKey(ColTerm.referenceID, ColTerm.Reference, ColTerm.ID))
      .put(ColTerm.nameID, new ForeignKey(ColTerm.nameID, ColTerm.Name, ColTerm.ID))
      .put(ColTerm.relatedNameID, new ForeignKey(ColTerm.relatedNameID, ColTerm.Name, ColTerm.ID))
      .put(ColTerm.taxonID, new ForeignKey(ColTerm.taxonID, ColTerm.Taxon, ColTerm.ID))
      .put(ColTerm.parentID, new ForeignKey(ColTerm.parentID, ColTerm.Taxon, ColTerm.ID))
      .build();

  private static final Set<ColTerm> required = ImmutableSet.of(ColTerm.ID, ColTerm.scientificName);
  
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
      pd.setResources(ColTerm.RESOURCES.keySet().stream()
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
    ColTerm rowType = ColTerm.find(FilenameUtils.getBaseName(resource), true);
    if (rowType == null) {
      throw new UnknownEntityException("Unknown entity " + resource);

    } else if (!rowType.isClass()) {
      throw new UnknownEntityException(resource + " is not a class term");
    }
    
    Schema s = new Schema();
    s.setRowType(rowType);
    
    for (ColTerm t : ColTerm.RESOURCES.get(rowType)) {
      String type = dataTypes.getOrDefault(t, Field.TYPE_STRING);
      String format = dataFormats.getOrDefault(t, Field.FORMAT_DEFAULT);
      Map<String, Object> constraints = new HashMap<>();
      if (enums.containsKey(t)) {
        constraints.put(Field.CONSTRAINT_KEY_ENUM, enumValues(rowType, t));
      }
      if (required.contains(t)) {
        constraints.put(Field.CONSTRAINT_KEY_REQUIRED, true);
      }
      if (ColTerm.ID == t) {
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
  
  private List<String> enumValues(ColTerm rowType, ColTerm t) {
    // special cases
    switch (t) {
      case country:
        return enumValues(Country.class, Country::getIso2LetterCode);
      case language:
        return enumValues(Language.class, Language::getIso3LetterCode);
    }
    
    Class<? extends Enum> enumClass;
    if (t == ColTerm.status && rowType == ColTerm.Name) {
      enumClass = NomStatus.class;
      
    } else if (t == ColTerm.type && rowType == ColTerm.Media) {
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
