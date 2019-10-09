package org.col.es.name;

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
import org.col.api.model.SimpleNameClassification;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.common.tax.SciNameNormalizer;
import org.col.es.EsModule;
import org.col.es.model.Monomial;
import org.col.es.model.NameStrings;
import org.col.es.model.NameUsageDocument;

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
import static org.col.api.vocab.NameField.SPECIFIC_EPITHET;
import static org.col.api.vocab.NameField.UNINOMIAL;
import static org.col.api.vocab.NameField.WEBPAGE;
import static org.col.common.collection.CollectionUtils.notEmpty;

/**
 * Converts a NameUsageWrapper instance into a NameUsage document. Note that the <i>entire</i> NameUsageWrapper instance
 * is serialized (and possibly zipped) and placed into the payload field of the NameUsage document.
 */
public class NameUsageWrapperConverter {

  /**
   * Whether or not to zip the stringified NameUsageWrapper.
   */
  public static final boolean ZIP_PAYLOAD = true;

  /**
   * Provides a weakly normalized version of the provided string (typically a scientific name). Whatever normalization
   * method we choose, we must make sure it is used both at index time (here) and at query time (QTranslator). Hence this
   * public static method.
   */
  public static String normalizeWeakly(String s) {
    if (s == null) {
      return null;
    }
    return SciNameNormalizer.normalize(s.toLowerCase());
  }

  /**
   * Provides a strongly normalized version of the provided string (typically a scientific name). For strong normalization
   * it is even more important that this method is used both at index time and at query time, because the order in which
   * the string is lowercased and normalized matters! Subtle bugs will arise if the order is different at index time and
   * at query time.
   */
  public static String normalizeStrongly(String s) {
    if (s == null) {
      return null;
    }
    return SciNameNormalizer.normalizeAll(s.toLowerCase());
  }

  /**
   * Extracts the classification from the provided document.
   * 
   * @param doc
   * @return
   */
  public static List<SimpleName> extractClassifiction(NameUsageDocument doc) {
    if (doc.getClassificationIds() == null) {
      return null;
    }
    List<String> ids = doc.getClassificationIds();
    List<Monomial> monomials = doc.getClassification();
    List<SimpleName> classification = new ArrayList<>(ids.size());
    for (int i = 0; i < ids.size(); i++) {
      Monomial m = monomials.get(i);
      classification.add(new SimpleName(ids.get(i), m.getName(), m.getRank()));
    }
    return classification;
  }

  /**
   * Copies the classification from the name usage object to the document.
   * 
   * @param from
   * @param to
   */
  public static void saveClassification(SimpleNameClassification from, NameUsageDocument to) {
    if (notEmpty(from.getClassification())) {
      int sz = from.getClassification().size();
      List<String> ids = new ArrayList<>(sz);
      List<Monomial> monomials = new ArrayList<>(sz);
      for (SimpleName sn : from.getClassification()) {
        ids.add(sn.getId());
        monomials.add(new Monomial(sn.getRank(), sn.getName()));
      }
      to.setClassification(monomials);
      to.setClassificationIds(ids);
    }
  }

  /**
   * Nullifies fields in the NameUsageWrapper object that are already indexed separately so as to make the payload (and
   * the entire document) as small as possible and to cut down as much as possible on JSON processing. It's not necessary
   * to prune away everything that can be pruned away, as long as this method mirrors enrichPayload().
   * 
   * @param nuw
   */
  public static void prunePayload(NameUsageWrapper nuw) {
    nuw.getUsage().setId(null);
    nuw.setDecisionKey(null);
    nuw.setPublisherKey(null);
    nuw.setIssues(null);
    nuw.setClassification(null);
    Name name = nuw.getUsage().getName();
    name.setDatasetKey(null);
    name.setId(null);
    name.setScientificName(null);
    name.setNameIndexId(null);
    name.setNomStatus(null);
    name.setPublishedInId(null);
    name.setType(null);
    if (nuw.getUsage().getClass() == Taxon.class) {
      ((Taxon) nuw.getUsage()).setSectorKey(null);
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.getAccepted().setSectorKey(null);
      s.getAccepted().getName().setScientificName(null);
    }
    if (notEmpty(nuw.getVernacularNames())) {
      nuw.getVernacularNames().forEach(vn -> vn.setName(null));
    }
  }

  /**
   * Puts the nullified fields back onto the NameUsageWrapper object.
   * 
   * @param nuw
   * @param doc
   */
  public static void enrichPayload(NameUsageWrapper nuw, NameUsageDocument doc) {
    nuw.getUsage().setId(doc.getUsageId());
    nuw.setDecisionKey(doc.getDecisionKey());
    nuw.setPublisherKey(doc.getPublisherKey());
    nuw.setIssues(doc.getIssues());
    nuw.setClassification(extractClassifiction(doc));
    Name name = nuw.getUsage().getName();
    name.setDatasetKey(doc.getDatasetKey());
    name.setId(doc.getNameId());
    name.setScientificName(doc.getScientificName());
    name.setNameIndexId(doc.getNameIndexId());
    name.setNomStatus(doc.getNomStatus());
    name.setPublishedInId(doc.getPublishedInId());
    name.setType(doc.getType());
    if (nuw.getUsage().getClass() == Taxon.class) {
      ((Taxon) nuw.getUsage()).setSectorKey(doc.getSectorKey());
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.getAccepted().setSectorKey(doc.getSectorKey());
      s.getAccepted().getName().setScientificName(doc.getAcceptedName());
    }
    if (notEmpty(doc.getVernacularNames())) {
      for (int i = 0; i < doc.getVernacularNames().size(); ++i) {
        nuw.getVernacularNames().get(i).setName(doc.getVernacularNames().get(i));
      }
    }
  }

  /**
   * Converts a NameUsageWrapper to an Elasticsearch document. Main method of this class.
   * 
   * @param nuw
   * @return
   * @throws IOException
   */
  public NameUsageDocument toDocument(NameUsageWrapper nuw) throws IOException {
    NameUsageDocument doc = new NameUsageDocument();
    saveScientificName(nuw, doc);
    saveVernacularNames(nuw, doc);
    saveClassification(nuw, doc);
    doc.setIssues(nuw.getIssues());
    Name name = nuw.getUsage().getName();
    doc.setAuthorship(name.authorshipComplete());
    doc.setDatasetKey(name.getDatasetKey());
    doc.setDecisionKey(nuw.getDecisionKey());
    doc.setNameId(name.getId());
    doc.setNameIndexId(name.getNameIndexId());
    doc.setNomCode(name.getCode());
    doc.setNomStatus(name.getNomStatus());
    doc.setPublishedInId(name.getPublishedInId());
    doc.setPublisherKey(nuw.getPublisherKey());
    doc.setRank(name.getRank());
    doc.setStatus(nuw.getUsage().getStatus());
    doc.setUsageId(nuw.getUsage().getId());
    doc.setType(name.getType());
    doc.setNameFields(getNonNullNameFields(name));
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      doc.setSectorKey(t.getSectorKey());
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      doc.setSectorKey(s.getAccepted().getSectorKey());
      doc.setAcceptedName(s.getAccepted().getName().getScientificName());
    }
    prunePayload(nuw);
    if (ZIP_PAYLOAD) {
      doc.setPayload(deflate(nuw));
    } else {
      doc.setPayload(EsModule.write(nuw));
    }
    return doc;
  }

  private static void saveScientificName(NameUsageWrapper from, NameUsageDocument to) {
    to.setScientificName(from.getUsage().getName().getScientificName());
    to.setNameStrings(new NameStrings(from.getUsage().getName()));
  }

  private static void saveVernacularNames(NameUsageWrapper from, NameUsageDocument to) {
    if (notEmpty(from.getVernacularNames())) {
      List<String> names = from.getVernacularNames()
          .stream()
          .map(VernacularName::getName)
          .collect(Collectors.toList());
      to.setVernacularNames(names);
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
    addIfSet(fields, WEBPAGE, name.getWebpage());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
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
   * Deflates and base64-encodes the stringified NameUsageWrapper. NB you can't store raw byte arrays in Elasticsearch.
   * You must base64 encode them.
   */
  private static String deflate(NameUsageWrapper nuw) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
      EsModule.write(nuw, dos);
    }
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }

}
