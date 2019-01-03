package org.col.es;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;

import org.col.api.model.Name;
import org.col.api.model.SimpleName;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.common.tax.SciNameNormalizer;
import org.col.es.model.EsNameUsage;
import org.col.es.model.Monomial;

import static org.col.api.vocab.NameField.BASIONYM_AUTHORS;
import static org.col.api.vocab.NameField.BASIONYM_EX_AUTHORS;
import static org.col.api.vocab.NameField.BASIONYM_YEAR;
import static org.col.api.vocab.NameField.CANDIDATUS;
import static org.col.api.vocab.NameField.COMBINATION_AUTHORS;
import static org.col.api.vocab.NameField.COMBINATION_EX_AUTHORS;
import static org.col.api.vocab.NameField.COMBINATION_YEAR;
import static org.col.api.vocab.NameField.CULTIVAR_EPITHET;
import static org.col.api.vocab.NameField.GENUS;
import static org.col.api.vocab.NameField.INFRAGENERIC_EPITHET;
import static org.col.api.vocab.NameField.INFRASPECIFIC_EPITHET;
import static org.col.api.vocab.NameField.NOM_STATUS;
import static org.col.api.vocab.NameField.NOTHO;
import static org.col.api.vocab.NameField.PUBLISHED_IN_ID;
import static org.col.api.vocab.NameField.PUBLISHED_IN_PAGE;
import static org.col.api.vocab.NameField.REMARKS;
import static org.col.api.vocab.NameField.SANCTIONING_AUTHOR;
import static org.col.api.vocab.NameField.SOURCE_URL;
import static org.col.api.vocab.NameField.SPECIFIC_EPITHET;
import static org.col.api.vocab.NameField.STRAIN;
import static org.col.api.vocab.NameField.UNINOMIAL;
import static org.col.common.util.CollectionUtils.notEmpty;
import static org.col.es.EsModule.NAME_USAGE_WRITER;

/**
 * Converts NameUsageWrapper instances to EsNameUsage documents. Only a few fields are indexed. The remainder of the NameUsageWrapper is
 * converted to JSON and the resulting JSON string is stored in a payload field.
 */
public class NameUsageTransfer {

  /**
   * Whether or not to zip the stringified NameUsageWrapper.
   */
  public static final boolean ZIP_PAYLOAD = true;

  /**
   * Provides a weakly normalized version of the original scientific name. Whatever normalization method we choose, we must make sure it is
   * used both at index time (here) and at query time (QTranslator). Hence this public static method.
   */
  public static String normalizeWeakly(String sn) {
    return SciNameNormalizer.normalize(sn);
  }

  /**
   * Provides a strongly normalized version of the original scientific name.
   */
  public static String normalizeStrongly(String sn) {
    return SciNameNormalizer.normalizeAll(sn);
  }

  /**
   * Extracts the classification from the Elasticsearch document.
   * 
   * @param enu
   * @return
   */
  static List<SimpleName> extractClassifiction(EsNameUsage enu) {
    if (enu.getClassificationIds() == null) {
      return null;
    }
    List<String> ids = enu.getClassificationIds();
    List<Monomial> monomials = enu.getClassification();
    List<SimpleName> classification = new ArrayList<>(ids.size());
    for (int i = 0; i < ids.size(); i++) {
      Monomial m = monomials.get(i);
      classification.add(new SimpleName(ids.get(i), m.getName(), m.getRank()));
    }
    return classification;
  }

  /**
   * Nullifies fields in the NameUsageWrapper object that are already indexed separately so as to make the payload (and the entire document)
   * as small as possible and to cut down as much as possible on JSON processing. It's not necessary to prune away everything that can be
   * pruned away, as long as this method mirrors enrichPayload().
   * 
   * @param nuw
   */
  static void prunePayload(NameUsageWrapper nuw) {
    nuw.getUsage().setId(null);
    nuw.getUsage().getName().setDatasetKey(null);
    nuw.getUsage().getName().setId(null);
    nuw.getUsage().getName().setIndexNameId(null);
    nuw.getUsage().getName().setNomStatus(null);
    nuw.getUsage().getName().setPublishedInId(null);
    nuw.getUsage().getName().setType(null);
    nuw.setIssues(null);
    nuw.setClassification(null);
  }

  /**
   * Puts the nullified fields back onto the NameUsageWrapper object.
   * 
   * @param nuw
   * @param enu
   */
  static void enrichPayload(NameUsageWrapper nuw, EsNameUsage enu) {
    nuw.getUsage().setId(enu.getUsageId());
    nuw.getUsage().getName().setDatasetKey(enu.getDatasetKey());
    nuw.getUsage().getName().setId(enu.getNameId());
    nuw.getUsage().getName().setIndexNameId(enu.getIndexNameId());
    nuw.getUsage().getName().setNomStatus(enu.getNomStatus());
    nuw.getUsage().getName().setPublishedInId(enu.getPublishedInId());
    nuw.getUsage().getName().setType(enu.getType());
    nuw.setIssues(enu.getIssues());
    nuw.setClassification(extractClassifiction(enu));
  }

  /**
   * Converts a NameUsageWrapper to an Elasticsearch document. Main method of this class.
   * 
   * @param nuw
   * @return
   * @throws IOException
   */
  EsNameUsage toDocument(NameUsageWrapper nuw) throws IOException {
    EsNameUsage enu = new EsNameUsage();
    saveScientificName(nuw, enu);
    saveVernacularNames(nuw, enu);
    saveClassification(nuw, enu);
    enu.setIssues(nuw.getIssues());
    Name name = nuw.getUsage().getName();
    enu.setAuthorship(name.authorshipComplete());
    enu.setDatasetKey(name.getDatasetKey());
    enu.setNameId(name.getId());
    enu.setIndexNameId(name.getIndexNameId());
    enu.setNomStatus(name.getNomStatus());
    enu.setPublishedInId(name.getPublishedInId());
    enu.setRank(name.getRank());
    enu.setStatus(nuw.getUsage().getStatus());
    enu.setUsageId(nuw.getUsage().getId());
    enu.setType(name.getType());
    enu.setNameFields(getNonNullNameFields(name));
    prunePayload(nuw);
    if (ZIP_PAYLOAD) {
      enu.setPayload(deflate(nuw));
    } else {
      enu.setPayload(NAME_USAGE_WRITER.writeValueAsString(nuw));
    }
    return enu;
  }

  private static void saveScientificName(NameUsageWrapper from, EsNameUsage to) {
    String w = normalizeWeakly(from.getUsage().getName().getScientificName());
    String s = normalizeStrongly(from.getUsage().getName().getScientificName());
    to.setScientificNameWN(w);
    /*
     * Don't waste time indexing the same ngram tokens twice. Only index the strongly normalized variant if it differs from the weakly
     * normalized variant. This if-logic is replicated at query time (see QTranslator).
     */
    if (!w.equals(s)) {
      to.setScientificNameSN(s);
    }
  }

  private static void saveVernacularNames(NameUsageWrapper from, EsNameUsage to) {
    if (notEmpty(from.getVernacularNames())) {
      List<String> names = from.getVernacularNames()
          .stream()
          .map(VernacularName::getName)
          .collect(Collectors.toList());
      to.setVernacularNames(names);
    }
  }

  private static void saveClassification(NameUsageWrapper from, EsNameUsage to) {
    if (notEmpty(from.getClassification())) {
      int sz = from.getClassification().size();
      List<String> ids = new ArrayList<>(sz);
      List<Monomial> monomials = new ArrayList<>(sz);
      SimpleName sn;
      for (int i = 0; i < sz; i++) {
        ids.add((sn = from.getClassification().get(i)).getId());
        monomials.add(new Monomial(sn.getRank(), sn.getName()));
      }
      to.setClassification(monomials);
      to.setClassificationIds(ids);
    }
  }

  private static Set<NameField> getNonNullNameFields(Name name) {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    if (name.getBasionymAuthorship() != null) {
      addIfSet(fields, BASIONYM_AUTHORS, name.getBasionymAuthorship().getAuthors());
      addIfSet(fields, BASIONYM_EX_AUTHORS, name.getBasionymAuthorship().getExAuthors());
      addIfSet(fields, BASIONYM_YEAR, name.getBasionymAuthorship().getYear());
    }
    if (name.getCombinationAuthorship() != null) {
      addIfSet(fields, COMBINATION_AUTHORS, name.getCombinationAuthorship().getAuthors());
      addIfSet(fields, COMBINATION_EX_AUTHORS, name.getCombinationAuthorship().getExAuthors());
      addIfSet(fields, COMBINATION_YEAR, name.getCombinationAuthorship().getYear());
    }
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, NOTHO, name.getNotho());
    addIfSet(fields, PUBLISHED_IN_ID, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, REMARKS, name.getRemarks());
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
    addIfSet(fields, SOURCE_URL, name.getSourceUrl());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
    addIfSet(fields, STRAIN, name.getStrain());
    addIfSet(fields, UNINOMIAL, name.getUninomial());
    if (name.isCandidatus()) {
      fields.add(CANDIDATUS);
    }
    return fields;
  }

  private static void addIfSet(Set<NameField> fields, NameField nf, Collection<?> val) {
    if (notEmpty(val)) {
      fields.add(nf);
    }
  }

  private static void addIfSet(Set<NameField> fields, NameField nf, Object val) {
    if (val != null) {
      fields.add(nf);
    }
  }

  /*
   * Deflates and base64 encodes the stringified NameUsageWrapper. NB you can't store raw byte arrays in Elasticsearch. You must base64
   * encode them.
   */
  private static String deflate(NameUsageWrapper nuw) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
      NAME_USAGE_WRITER.writeValue(dos, nuw);
    }
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }

}
