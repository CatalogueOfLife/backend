package life.catalogue.es.nu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import life.catalogue.es.DownwardConverter;
import life.catalogue.es.EsDecision;
import life.catalogue.es.EsModule;
import life.catalogue.es.EsMonomial;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.NameStrings;
import static life.catalogue.api.vocab.NameField.*;
import static life.catalogue.common.collection.CollectionUtils.notEmpty;

/**
 * Converts {@link NameUsageWrapper} instances into a {@link EsNameUsage} instances (which model the documents entering Elasticsearch. Note
 * that the <i>entire</i> NameUsageWrapper instance is serialized (and possibly zipped) and placed into the payload field of the NameUsage
 * document.
 */
public class NameUsageWrapperConverter implements DownwardConverter<NameUsageWrapper, EsNameUsage> {

  /**
   * Whether or not to zip the stringified NameUsageWrapper.
   */
  public static final boolean ZIP_PAYLOAD = true;
  // See https://github.com/CatalogueOfLife/backend/issues/200
  private static final boolean INCLUDE_VERNACLUAR_NAMES = false;

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
    nuw.getUsage().setId(null);
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
    nuw.getUsage().setId(doc.getUsageId());
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
      if (INCLUDE_VERNACLUAR_NAMES) {
        for (int i = 0; i < doc.getVernacularNames().size(); ++i) {
          nuw.getVernacularNames().get(i).setName(doc.getVernacularNames().get(i));
        }
      } else {
        nuw.setVernacularNames(Collections.EMPTY_LIST);
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
  public EsNameUsage toDocument(NameUsageWrapper nuw) throws IOException {
    EsNameUsage doc = new EsNameUsage();
    saveScientificName(nuw, doc);
    saveAuthorship(nuw, doc);
    saveVernacularNames(nuw, doc);
    saveClassification(doc, nuw);
    saveDecisions(nuw, doc);
    doc.setIssues(nuw.getIssues());
    Name name = nuw.getUsage().getName();
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

  private static void saveScientificName(NameUsageWrapper nuw, EsNameUsage doc) {
    doc.setScientificName(nuw.getUsage().getName().getScientificName());
    doc.setNameStrings(new NameStrings(nuw.getUsage().getName()));
  }

  private static void saveAuthorship(NameUsageWrapper nuw, EsNameUsage doc) {
    Name name = nuw.getUsage().getName();
    doc.setAuthorshipComplete(name.authorshipComplete());
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

  private static void saveVernacularNames(NameUsageWrapper nuw, EsNameUsage doc) {
    if (notEmpty(nuw.getVernacularNames())) {
      List<String> names = nuw.getVernacularNames()
          .stream()
          .map(VernacularName::getName)
          .collect(Collectors.toList());
      doc.setVernacularNames(names);
    }
  }

  private static void saveDecisions(NameUsageWrapper nuw, EsNameUsage doc) {
    if (notEmpty(nuw.getDecisions())) {
      List<EsDecision> decisions = nuw.getDecisions()
          .stream()
          .map(EsDecision::from)
          .collect(Collectors.toList());
      doc.setDecisions(decisions);
      doc.setDecisionCount(nuw.getDecisions().size());
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
