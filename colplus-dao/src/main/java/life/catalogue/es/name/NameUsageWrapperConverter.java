package life.catalogue.es.name;

import static life.catalogue.api.vocab.NameField.BASIONYM_AUTHORS;
import static life.catalogue.api.vocab.NameField.BASIONYM_EX_AUTHORS;
import static life.catalogue.api.vocab.NameField.BASIONYM_YEAR;
import static life.catalogue.api.vocab.NameField.CANDIDATUS;
import static life.catalogue.api.vocab.NameField.CODE;
import static life.catalogue.api.vocab.NameField.COMBINATION_AUTHORS;
import static life.catalogue.api.vocab.NameField.COMBINATION_EX_AUTHORS;
import static life.catalogue.api.vocab.NameField.COMBINATION_YEAR;
import static life.catalogue.api.vocab.NameField.CULTIVAR_EPITHET;
import static life.catalogue.api.vocab.NameField.GENUS;
import static life.catalogue.api.vocab.NameField.INFRAGENERIC_EPITHET;
import static life.catalogue.api.vocab.NameField.INFRASPECIFIC_EPITHET;
import static life.catalogue.api.vocab.NameField.LINK;
import static life.catalogue.api.vocab.NameField.NOM_STATUS;
import static life.catalogue.api.vocab.NameField.NOTHO;
import static life.catalogue.api.vocab.NameField.PUBLISHED_IN_ID;
import static life.catalogue.api.vocab.NameField.PUBLISHED_IN_PAGE;
import static life.catalogue.api.vocab.NameField.REMARKS;
import static life.catalogue.api.vocab.NameField.SANCTIONING_AUTHOR;
import static life.catalogue.api.vocab.NameField.SPECIFIC_EPITHET;
import static life.catalogue.api.vocab.NameField.UNINOMIAL;
import static life.catalogue.common.collection.CollectionUtils.notEmpty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import org.apache.commons.io.IOUtils;
import life.catalogue.api.model.BareName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.NameField;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.es.EsModule;
import life.catalogue.es.model.EsDecision;
import life.catalogue.es.model.Monomial;
import life.catalogue.es.model.NameStrings;
import life.catalogue.es.model.NameUsageDocument;

/**
 * Converts a NameUsageWrapper instance into a NameUsage document. Note that the <i>entire</i> NameUsageWrapper instance is serialized (and
 * possibly zipped) and placed into the payload field of the NameUsage document.
 */
public class NameUsageWrapperConverter {

  /**
   * Whether or not to zip the stringified NameUsageWrapper.
   */
  public static final boolean ZIP_PAYLOAD = true;

  /**
   * Serializes, deflates and base64-encodes a NameUsageWrapper. NB you can't store raw byte arrays in Elasticsearch. You must base64-encode
   * them.
   */
  public static String deflate(NameUsageWrapper nuw) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
      EsModule.write(dos, nuw);
    }
    byte[] bytes = Base64.getEncoder().encode(baos.toByteArray());
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Base64-decodes, unzips and deserializes the provided payload string back to a NameUsageWrapper instance.
   * 
   * @param payload
   * @return
   * @throws IOException
   */
  public static NameUsageWrapper inflate(String payload) throws IOException {
    byte[] bytes = Base64.getDecoder().decode(payload.getBytes());
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    return EsModule.readNameUsageWrapper(new InflaterInputStream(bais));
  }

  /**
   * Base64-decodes and unzips the provided payload string. For testing purposes only.
   * 
   * @param payload
   * @return
   * @throws IOException
   */
  public static String inflateToJson(String payload) throws IOException {
    byte[] bytes = Base64.getDecoder().decode(payload.getBytes());
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    InputStream is = new InflaterInputStream(bais);
    return IOUtils.toString(is, StandardCharsets.UTF_8);
  }

  /**
   * Provides a weakly normalized version of the provided string (typically a scientific name). Whatever normalization method we choose, we
   * must make sure it is used both at index time (here) and at query time (QTranslator). Hence this public static method.
   */
  public static String normalizeWeakly(String s) {
    if (s == null) {
      return null;
    }
    return SciNameNormalizer.normalize(s.toLowerCase());
  }

  /**
   * Provides a strongly normalized version of the provided string (typically a scientific name). For strong normalization it is even more
   * important that this method is used both at index time and at query time, because the order in which the string is lowercased and
   * normalized matters! Subtle bugs will arise if the order is different at index time and at query time.
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
   * Nullifies fields in the NameUsageWrapper object that are already indexed separately so as to make the payload (and the entire document)
   * as small as possible and to cut down as much as possible on JSON processing. It's not necessary to prune away everything that can be
   * pruned away, as long as this method mirrors enrichPayload().
   * 
   * @param nuw
   */
  public static void prunePayload(NameUsageWrapper nuw) {
    nuw.getUsage().setId(null);
    nuw.setPublisherKey(null);
    nuw.setIssues(null);
    nuw.setClassification(null);
    Name name = nuw.getUsage().getName();
    name.setDatasetKey(null);
    name.setId(null);
    name.setScientificName(null);
    name.setSpecificEpithet(null);
    name.setInfraspecificEpithet(null);
    name.setNameIndexId(null);
    name.setNomStatus(null);
    name.setPublishedInId(null);
    name.setType(null);
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      t.setDatasetKey(null);
      t.setSectorKey(null);
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.setDatasetKey(null);
      s.getAccepted().setDatasetKey(null);
      s.getAccepted().setSectorKey(null);
      s.getAccepted().getName().setScientificName(null);
    } else {
      BareName b = (BareName) nuw.getUsage();
      b.setDatasetKey(null);
    }
    if (notEmpty(nuw.getVernacularNames())) {
      nuw.getVernacularNames().forEach(vn -> vn.setName(null));
    }
    if (notEmpty(nuw.getDecisions())) {
      nuw.getDecisions().stream().forEach(d -> {
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
  public static void enrichPayload(NameUsageWrapper nuw, NameUsageDocument doc) {
    nuw.getUsage().setId(doc.getUsageId());
    nuw.setPublisherKey(doc.getPublisherKey());
    nuw.setIssues(doc.getIssues());
    nuw.setClassification(extractClassifiction(doc));
    Name name = nuw.getUsage().getName();
    name.setDatasetKey(doc.getDatasetKey());
    name.setId(doc.getNameId());
    name.setScientificName(doc.getScientificName());
    name.setSpecificEpithet(doc.getNameStrings().getSpecificEpithet());
    name.setInfraspecificEpithet(doc.getNameStrings().getInfraspecificEpithet());
    name.setNameIndexId(doc.getNameIndexId());
    name.setNomStatus(doc.getNomStatus());
    name.setPublishedInId(doc.getPublishedInId());
    name.setType(doc.getType());
    if (nuw.getUsage().getClass() == Taxon.class) {
      Taxon t = (Taxon) nuw.getUsage();
      t.setDatasetKey(doc.getDatasetKey());
      t.setSectorKey(doc.getSectorKey());
    } else if (nuw.getUsage().getClass() == Synonym.class) {
      Synonym s = (Synonym) nuw.getUsage();
      s.setDatasetKey(doc.getDatasetKey());
      s.getAccepted().setDatasetKey(doc.getDatasetKey());
      s.getAccepted().setSectorKey(doc.getSectorKey());
      s.getAccepted().getName().setScientificName(doc.getAcceptedName());
    } else {
      BareName b = (BareName) nuw.getUsage();
      b.setDatasetKey(doc.getDatasetKey());
    }
    if (notEmpty(doc.getVernacularNames())) {
      for (int i = 0; i < doc.getVernacularNames().size(); ++i) {
        nuw.getVernacularNames().get(i).setName(doc.getVernacularNames().get(i));
      }
    }
    if (notEmpty(doc.getDecisions())) {
      for (int i = 0; i < nuw.getDecisions().size(); ++i) {
        nuw.getDecisions().get(i).setDatasetKey(doc.getDecisions().get(i).getCatalogueKey());
        nuw.getDecisions().get(i).setMode(doc.getDecisions().get(i).getMode());
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
    saveDecisions(nuw, doc);
    doc.setIssues(nuw.getIssues());
    Name name = nuw.getUsage().getName();
    doc.setAuthorship(name.authorshipComplete());
    doc.setDatasetKey(name.getDatasetKey());
    doc.setSectorDatasetKey(nuw.getSectorDatasetKey());
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

  private static void saveScientificName(NameUsageWrapper nuw, NameUsageDocument doc) {
    doc.setScientificName(nuw.getUsage().getName().getScientificName());
    doc.setNameStrings(new NameStrings(nuw.getUsage().getName()));
  }

  private static void saveVernacularNames(NameUsageWrapper nuw, NameUsageDocument doc) {
    if (notEmpty(nuw.getVernacularNames())) {
      List<String> names = nuw.getVernacularNames()
          .stream()
          .map(VernacularName::getName)
          .collect(Collectors.toList());
      doc.setVernacularNames(names);
    }
  }

  private static void saveDecisions(NameUsageWrapper nuw, NameUsageDocument doc) {
    if (notEmpty(nuw.getDecisions())) {
      List<EsDecision> decisions = nuw.getDecisions()
          .stream()
          .map(EsDecision::from)
          .collect(Collectors.toList());
      doc.setDecisions(decisions);
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
    addIfSet(fields, CODE, name.getCode());
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, LINK, name.getLink());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, NOTHO, name.getNotho());
    addIfSet(fields, PUBLISHED_IN_ID, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, REMARKS, name.getRemarks());
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
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

}
