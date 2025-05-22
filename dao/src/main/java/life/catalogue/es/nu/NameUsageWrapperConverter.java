package life.catalogue.es.nu;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.NameField;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.es.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import static life.catalogue.api.vocab.NameField.*;
import static life.catalogue.common.collection.CollectionUtils.notEmpty;

/**
 * Converts {@link NameUsageWrapper} instances into a {@link EsNameUsage} instances (which model the documents entering Elasticsearch. Note
 * that the <i>entire</i> NameUsageWrapper instance is serialized (and possibly zipped) and placed into the payload field of the NameUsage
 * document.
 */
public class NameUsageWrapperConverter implements DownwardConverter<NameUsageWrapper, EsNameUsage> {
  final private static EsKryoPool pool = new EsKryoPool(8);
  final private static int bufferSize = 1024;


  /**
   * Serializes and base64-encodes a NameUsageWrapper with Kryo.
   * NB you can't store raw byte arrays in Elasticsearch. You must base64-encode them.
   */
  public static String encode(NameUsageWrapper nuw) throws IOException {
    Kryo kryo = pool.obtain();
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
      Output output = new Output(buffer, bufferSize);
      kryo.writeObject(output, nuw);
      output.close();
      byte[] bytes = Base64.getEncoder().encode(buffer.toByteArray());
      return new String(bytes, StandardCharsets.US_ASCII);

    } finally {
      pool.free(kryo);
    }
  }

  /**
   * Base64-decodes and deserializes the provided kryo payload string back to a NameUsageWrapper instance.
   * 
   * @param payload Base64 encoded kryo byte array for a NameUsageWrapper
   * @return
   * @throws IOException
   */
  public static NameUsageWrapper decode(String payload) throws IOException {
    Kryo kryo = pool.obtain();
    try {
      byte[] bytes = Base64.getDecoder().decode(payload.getBytes());
      return kryo.readObject(new Input(bytes), NameUsageWrapper.class);
    } finally {
      pool.free(kryo);
    }
  }

  /**
   * Provides a weakly normalized version of the provided string. Used to index generic epithets. See {@link NameStrings}.
   */
  public static String normalizeWeakly(String s) {
    if (s == null) {
      return null;
    }
    return SciNameNormalizer.normalize(s.toLowerCase());
  }

  /**
   * Provides a strongly normalized version of the provided string. Used to index specific epithets and infraspecific epithets.
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
  public static List<SimpleName> extractClassifiction(EsNameUsage doc) {
    if (doc.getClassificationIds() == null) {
      return null;
    }
    List<String> ids = doc.getClassificationIds();
    List<EsMonomial> monomials = doc.getClassification();
    List<SimpleName> classification = new ArrayList<>(ids.size());
    for (int i = 0; i < ids.size(); i++) {
      EsMonomial m = monomials.get(i);
      classification.add(new SimpleName(ids.get(i), m.getName(), m.getRank()));
    }
    return classification;
  }

  /**
   * Copies the provided classification into the provided document.
   * 
   * @param document
   * @param classification
   */
  public static void saveClassification(EsNameUsage document, SimpleNameClassification classification) {
    if (notEmpty(classification.getClassification())) {
      int sz = classification.getClassification().size();
      List<String> ids = new ArrayList<>(sz);
      List<EsMonomial> monomials = new ArrayList<>(sz);
      for (SimpleName sn : classification.getClassification()) {
        if (sn==null) continue; // shouldnt happen, but better be safe
        ids.add(sn.getId());
        monomials.add(new EsMonomial(sn.getRank(), sn.getName()));
      }
      document.setClassification(monomials);
      document.setClassificationIds(ids);
    }
  }

  /**
   * Nullifies fields in the NameUsageWrapper object that are already indexed separately so as to make the payload (and the entire document)
   * as small as possible and to cut down as much as possible on JSON processing. It's not necessary to prune away everything that can be
   * pruned away, as long as this method mirrors enrichPayload().
   *
   * @param nuw
   */
  public static void prunePayload(NameUsageWrapper nuw) {
    // wrapper
    nuw.setPublisherKey(null);
    nuw.setIssues(null);
    nuw.setGroup(null);
    nuw.setClassification(null);
    // name
    Name name = nuw.getUsage().getName();
    name.setId(null);
    name.setDatasetKey(null);
    name.setScientificName(null);
    name.setNomStatus(null);
    name.setPublishedInId(null);
    name.setType(null);
    // remove nidx which is not searchable and will get out of sync if the index is rebuilt
    name.setNamesIndexId(null);
    name.setNamesIndexType(null);
    // usage
    NameUsage u = nuw.getUsage();
    u.setId(null);
    u.setDatasetKey(null);
    u.setSectorKey(null);
    u.setSectorMode(null);
    u.setOrigin(null);
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      t.setExtinct(null);
      t.setEnvironments(null);
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.getAccepted().setDatasetKey(null);
      s.getAccepted().getName().setScientificName(null);
    }
    // decisions
    if (notEmpty(nuw.getDecisions())) {
      nuw.getDecisions().forEach(d -> {
        d.setDatasetKey(null);
        d.setMode(null);
      });
    }
  }

  /**
   * Puts the nullified fields back onto the NameUsageWrapper object.
   * 
   * @param nuw
   * @param doc
   */
  public static void enrichPayload(NameUsageWrapper nuw, EsNameUsage doc) {
    // wrapper
    nuw.setPublisherKey(doc.getPublisherKey());
    nuw.setIssues(doc.getIssues());
    nuw.setGroup(doc.getGroup());
    nuw.setClassification(extractClassifiction(doc));
    // name
    Name name = nuw.getUsage().getName();
    name.setDatasetKey(doc.getDatasetKey());
    name.setId(doc.getNameId());
    name.setScientificName(doc.getScientificName());
    name.setNomStatus(doc.getNomStatus());
    name.setPublishedInId(doc.getPublishedInId());
    name.setType(doc.getType());
    // nidx will be empty!
    // usage
    NameUsage u = nuw.getUsage();
    u.setId(doc.getUsageId());
    u.setDatasetKey(doc.getDatasetKey());
    u.setSectorKey(doc.getSectorKey());
    u.setSectorMode(doc.getSectorMode());
    u.setOrigin(doc.getOrigin());
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      t.setExtinct(doc.getExtinct());
      t.setEnvironments(doc.getEnvironments());
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.getAccepted().setDatasetKey(doc.getDatasetKey());
      s.getAccepted().getName().setScientificName(doc.getAcceptedName());
    }
    // decisions
    if (notEmpty(doc.getDecisions())) {
      for (int i = 0; i < nuw.getDecisions().size(); ++i) {
        nuw.getDecisions().get(i).setDatasetKey(doc.getDecisions().get(i).getCatalogueKey());
        nuw.getDecisions().get(i).setMode(doc.getDecisions().get(i).getMode());
      }
    }
  }

  /**
   * Converts a NameUsageWrapper to an Elasticsearch document. Main method of this class.
   * Warning !!!
   * This method modifies the original NameUsageWrapper instance and nullifies some values.
   * Be sure to make a defensive copy if you don't want that!
   * 
   * @param nuw
   * @return
   * @throws IOException
   */
  public static EsNameUsage toDocument(NameUsageWrapper nuw) throws IOException {
    EsNameUsage doc = new EsNameUsage();
    // wrapper
    doc.setIssues(nuw.getIssues());
    doc.setGroup(nuw.getGroup());
    doc.setPublisherKey(nuw.getPublisherKey());
    doc.setSectorDatasetKey(nuw.getSectorDatasetKey());
    doc.setSectorPublisherKey(nuw.getSectorPublisherKey());
    // name
    saveScientificName(nuw, doc);
    saveAuthorship(nuw, doc);
    Name name = nuw.getUsage().getName();
    doc.setNameId(name.getId());
    doc.setNomCode(name.getCode());
    doc.setNomStatus(name.getNomStatus());
    doc.setPublishedInId(name.getPublishedInId());
    doc.setRank(name.getRank());
    doc.setType(name.getType());
    doc.setNameFields(getNonNullNameFields(nuw.getUsage()));
    // usage
    doc.setUsageId(nuw.getUsage().getId());
    doc.setDatasetKey(ObjectUtils.coalesce(nuw.getUsage().getDatasetKey(), name.getDatasetKey()));
    doc.setSectorKey(ObjectUtils.coalesce(nuw.getUsage().getSectorKey(), name.getSectorKey()));
    doc.setSectorMode(ObjectUtils.coalesce(nuw.getUsage().getSectorMode(), name.getSectorMode()));
    doc.setOrigin(nuw.getUsage().getOrigin());
    doc.setStatus(nuw.getUsage().getStatus());
    doc.setSecondarySourceGroup(nuw.getSecondarySourceGroups());
    doc.setSecondarySourceKey(nuw.getSecondarySourceKeys());
    saveClassification(doc, nuw);
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      doc.setExtinct(t.isExtinct());
      doc.setEnvironments(t.getEnvironments());
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      doc.setSectorKey(s.getAccepted().getSectorKey());
      doc.setAcceptedName(s.getAccepted().getName().getScientificName());
    }
    // decision
    saveDecisions(nuw, doc);
    prunePayload(nuw);
    doc.setPayload(encode(nuw));
    return doc;
  }

  private static void saveScientificName(NameUsageWrapper nuw, EsNameUsage doc) {
    doc.setScientificName(nuw.getUsage().getName().getScientificName());
    doc.setNameStrings(new NameStrings(nuw.getUsage().getName()));
  }

  private static void saveAuthorship(NameUsageWrapper nuw, EsNameUsage doc) {
    Name name = nuw.getUsage().getName();
    doc.setAuthorshipComplete(name.getAuthorship());
    Set<String> authorship = new TreeSet<>();
    Set<String> year = new TreeSet<>();
    if (name.getBasionymAuthorship() != null) {
      if (name.getBasionymAuthorship().getYear() != null) {
        year.add(name.getBasionymAuthorship().getYear());
      }
      if (name.getBasionymAuthorship().getAuthors() != null) {
        authorship.addAll(name.getBasionymAuthorship().getAuthors());
      }
      if (name.getBasionymAuthorship().getExAuthors() != null) {
        authorship.addAll(name.getBasionymAuthorship().getExAuthors());
      }
    }
    if (name.getCombinationAuthorship() != null) {
      if (name.getCombinationAuthorship().getYear() != null) {
        year.add(name.getCombinationAuthorship().getYear());
      }
      if (name.getCombinationAuthorship().getAuthors() != null) {
        authorship.addAll(name.getCombinationAuthorship().getAuthors());
      }
      if (name.getCombinationAuthorship().getExAuthors() != null) {
        authorship.addAll(name.getCombinationAuthorship().getExAuthors());
      }
    }
    if (!authorship.isEmpty()) {
      doc.setAuthorship(authorship);
    }
    if (!year.isEmpty()) {
      doc.setAuthorshipYear(year);
    }
  }

  private static void saveDecisions(NameUsageWrapper nuw, EsNameUsage doc) {
    if (notEmpty(nuw.getDecisions())) {
      List<EsDecision> decisions = nuw.getDecisions()
          .stream()
          .map(EsDecision::from)
          .collect(Collectors.toList());
      doc.setDecisions(decisions);
    }
  }

  private static Set<NameField> getNonNullNameFields(NameUsage usage) {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    Name name = usage.getName();
    addIfSet(fields, UNINOMIAL, name.getUninomial());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    if (name.isCandidatus()) {
      fields.add(CANDIDATUS);
    }
    addIfSet(fields, NOTHO, name.getNotho());
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
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
    addIfSet(fields, CODE, name.getCode());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, PUBLISHED_IN, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, NOMENCLATURAL_NOTE, name.getNomenclaturalNote());
    addIfSet(fields, UNPARSED, name.getUnparsed());
    addIfSet(fields, REMARKS, name.getRemarks());
  // NameUsage fields
    addIfSet(fields, REMARKS, usage.getRemarks());
    addIfSet(fields, NAME_PHRASE, usage.getNamePhrase());
    addIfSet(fields, ACCORDING_TO, usage.getAccordingToId());
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

}
