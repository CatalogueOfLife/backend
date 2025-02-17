package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.csv.ColdpReader;
import life.catalogue.img.ImageService;
import life.catalogue.metadata.coldp.DatasetYamlWriter;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ColDP exporter using a merged NameUsage entity and usageIDs instead of nameIDs for linking.
 */
public class ColdpExtendedExport extends ArchiveExport {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpExtendedExport.class);
  private static final String METADATA_FILENAME = "metadata.yaml";
  private Writer cslWriter;
  private boolean cslFirst = true;
  private NameUsageKeyMap nameUsageKeyMap;
  private final File treatmentDir;

  public ColdpExtendedExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(DataFormat.COLDP, userKey, req, factory, cfg, imageService);
    if (inclTreatments) {
      treatmentDir = new File(tmpDir, "treatments");
      treatmentDir.mkdirs();
    } else {
      treatmentDir = null;
    }
  }

  @Override
  protected void init(SqlSession session) throws Exception {
    super.init(session);
    nameUsageKeyMap = new NameUsageKeyMap(datasetKey, session);
  }

  @Override
  Term[] define(EntityType entity) {
    if (ColdpTerm.RESOURCES.containsKey(entity.coldp)) {
      var terms = new LinkedList<>(ColdpTerm.RESOURCES.get(entity.coldp));
      terms.push(entity.coldp);
      return terms.toArray(Term[]::new);
    }
    LOG.warn("{} ENTITY NOT MAPPED", entity);
    return null;
  }

  @Override
  void write(NameUsageBase u) {
    // name usage records come first - this allows us to keep a mapping of nameIDs to usageIDs
    // which we will use instead of nameIDs exclusively in the exporter as we use NameUsage records.
    nameUsageKeyMap.add(u.getName().getId(), u.getId());

    write(u.getName());
    writer.set(ColdpTerm.ID, u.getId());
    writer.set(ColdpTerm.sourceID, sector2datasetKey(u.getSectorKey()));
    writer.set(ColdpTerm.parentID, u.getParentId());
    writer.set(ColdpTerm.status, u.getStatus());
    writer.set(ColdpTerm.namePhrase, u.getNamePhrase());
    writer.set(ColdpTerm.accordingToID, u.getAccordingToId());
    writer.set(ColdpTerm.referenceID, u.getReferenceIds());
    // see taxon specifics below
    writer.set(ColdpTerm.link, u.getLink());
    writer.set(ColdpTerm.remarks, u.getRemarks());

    if (!u.isSynonym()) {
      TaxonWithClassification t = (TaxonWithClassification) u;
      writer.set(ColdpTerm.scrutinizer, t.getScrutinizer());
      writer.set(ColdpTerm.scrutinizerID, t.getScrutinizerID());
      writer.set(ColdpTerm.scrutinizerDate, t.getScrutinizerDate());
      writer.set(ColdpTerm.extinct, t.isExtinct());
      writer.set(ColdpTerm.temporalRangeStart, t.getTemporalRangeStart());
      writer.set(ColdpTerm.temporalRangeEnd, t.getTemporalRangeEnd());
      writer.set(ColdpTerm.environment, t.getEnvironments(), PermissiveEnumSerde::enumValueName);
      writer.set(ColdpTerm.ordinal, t.getOrdinal());
      if (t.getClassification() != null) {
        for (var ht : t.getClassification()) {
          if (ColdpReader.RANK2COLDP.containsKey(ht.getRank())) {
            writer.set(ColdpReader.RANK2COLDP.get(ht.getRank()), ht.getName());
          }
        }
      }
    }
  }

  void write(BareName u) {
    write(u.getName());
    // we need to make sure the nameID does not clash with a usageID, see https://github.com/CatalogueOfLife/backend/issues/1046
    // bare names come after all other usages, so we can check our map
    String bareID = u.getName().getId();
    Integer counter = null;
    while (nameUsageKeyMap.containsUsageID(bareID)) {
      // we need to invent some new unique id :(
      if (counter == null) {
        bareID = "BN-"+bareID;
        counter = 2;
      } else {
        bareID = "BN" + counter + "-" + u.getName().getId();
        counter++;
      }
    }
    nameUsageKeyMap.add(u.getName().getId(), bareID);
    writer.set(ColdpTerm.ID, bareID);
    writer.set(ColdpTerm.status, TaxonomicStatus.BARE_NAME);
    writer.set(ColdpTerm.remarks, u.getRemarks());
  }

  void write(Name n) {
    writer.set(ColdpTerm.sourceID, sector2datasetKey(n.getSectorKey()));
    for (NameRelation rel : nameRelMapper.listByType(n, NomRelType.BASIONYM)) {
      writer.set(ColdpTerm.basionymID, nameUsageKeyMap.getFirst(rel.getRelatedNameId()));
    }
    writer.set(ColdpTerm.scientificName, n.getScientificName());
    writer.set(ColdpTerm.authorship, n.getAuthorship());
    writer.set(ColdpTerm.rank, n.getRank());
    // parsed name
    writer.set(ColdpTerm.notho, n.getNotho());
    writer.set(ColdpTerm.uninomial, n.getUninomial());
    writer.set(ColdpTerm.genericName, n.getGenus());
    writer.set(ColdpTerm.infragenericEpithet, n.getInfragenericEpithet());
    writer.set(ColdpTerm.specificEpithet, n.getSpecificEpithet());
    writer.set(ColdpTerm.infraspecificEpithet, n.getInfraspecificEpithet());
    writer.set(ColdpTerm.cultivarEpithet, n.getCultivarEpithet());
    // parsed authorship
    var a = n.getCombinationAuthorship();
    if (a != null) {
      writer.set(ColdpTerm.combinationAuthorship, a.getAuthors(), "|");
      writer.set(ColdpTerm.combinationExAuthorship, a.getExAuthors(), "|");
      writer.set(ColdpTerm.combinationAuthorshipYear, a.getYear());
    }
    a = n.getBasionymAuthorship();
    if (a != null) {
      writer.set(ColdpTerm.basionymAuthorship, a.getAuthors(), "|");
      writer.set(ColdpTerm.basionymExAuthorship, a.getExAuthors(), "|");
      writer.set(ColdpTerm.basionymAuthorshipYear, a.getYear());
    }
    // other
    writer.set(ColdpTerm.originalSpelling, n.isOriginalSpelling());
    writer.set(ColdpTerm.genderAgreement, n.hasGenderAgreement());
    writer.set(ColdpTerm.gender, n.getGender());
    writer.set(ColdpTerm.etymology, n.getEtymology());
    writer.set(ColdpTerm.nameReferenceID, n.getPublishedInId());
    writer.set(ColdpTerm.publishedInYear, n.getPublishedInYear());
    writer.set(ColdpTerm.publishedInPage, n.getPublishedInPage());
    writer.set(ColdpTerm.publishedInPageLink, n.getPublishedInPageLink());
    writer.set(ColdpTerm.code, n.getCode());
    writer.set(ColdpTerm.nameStatus, n.getNomStatus());
    writer.set(ColdpTerm.link, n.getLink());
    writer.set(ColdpTerm.remarks, n.getRemarks());
  }

  @Override
  void write(String taxonID, VernacularName vn) {
    writer.set(ColdpTerm.taxonID, taxonID);
    writer.set(ColdpTerm.sourceID, sector2datasetKey(vn.getSectorKey()));
    writer.set(ColdpTerm.name, vn.getName());
    writer.set(ColdpTerm.transliteration, vn.getLatin());
    writer.set(ColdpTerm.language, vn.getLanguage());
    writer.set(ColdpTerm.country, vn.getCountry(), Country::getIso2LetterCode);
    writer.set(ColdpTerm.area, vn.getArea());
    writer.set(ColdpTerm.sex, vn.getSex());
    writer.set(ColdpTerm.remarks, vn.getRemarks());
  }

  @Override
  void writeTreatment(Treatment t) throws IOException {
    // single files in a folder
    String suffix = t.getFormat() == null ? "txt" : t.getFormat().getFileSuffix();
    File tf = new File(treatmentDir, t.getId() + "." + suffix);
    try (var tw = UTF8IoUtils.writerFromFile(tf)) {
      tw.write(t.getDocument());
    }
  }

  @Override
  void write(String taxonID, TaxonProperty tp) {
    writer.set(ColdpTerm.taxonID, taxonID);
    writer.set(ColdpTerm.sourceID, sector2datasetKey(tp.getSectorKey()));
    writer.set(ColdpTerm.property, tp.getProperty());
    writer.set(ColdpTerm.value, tp.getValue());
    writer.set(ColdpTerm.referenceID, tp.getReferenceId());
    writer.set(ColdpTerm.page, tp.getPage());
    writer.set(ColdpTerm.ordinal, tp.getOrdinal());
    writer.set(ColdpTerm.remarks, tp.getRemarks());
  }

  @Override
  public void exportReferences() throws IOException {
    super.exportReferences();
    if (cslWriter != null) {
      cslWriter.write("\n]\n");
      cslWriter.close();
    }
  }

  @Override
  void write(Reference r) throws IOException {
    writer.set(ColdpTerm.ID, r.getId());
    writer.set(ColdpTerm.sourceID, sector2datasetKey(r.getSectorKey()));
    writer.set(ColdpTerm.citation, r.getCitation());
    writer.set(ColdpTerm.remarks, r.getRemarks());
    if (r.getCsl() != null) {
      var csl = r.getCsl();
      writer.set(ColdpTerm.type, csl.getType());
      writer.set(ColdpTerm.author, csl.getAuthor());
      writer.set(ColdpTerm.editor, csl.getEditor());
      writer.set(ColdpTerm.title, csl.getTitle());
      writer.set(ColdpTerm.containerAuthor, csl.getContainerAuthor());
      writer.set(ColdpTerm.containerTitle, csl.getContainerTitle());
      writer.set(ColdpTerm.issued, csl.getIssued());
      writer.set(ColdpTerm.accessed, csl.getAccessed());
      writer.set(ColdpTerm.collectionTitle, csl.getCollectionTitle());
      writer.set(ColdpTerm.collectionEditor, csl.getCollectionEditor());
      writer.set(ColdpTerm.volume, csl.getVolume());
      writer.set(ColdpTerm.issue, csl.getIssue());
      writer.set(ColdpTerm.edition, csl.getEdition());
      writer.set(ColdpTerm.page, csl.getPage());
      writer.set(ColdpTerm.publisher, csl.getPublisher());
      writer.set(ColdpTerm.publisherPlace, csl.getPublisherPlace());
      writer.set(ColdpTerm.version, csl.getVersion());
      writer.set(ColdpTerm.isbn, csl.getISBN());
      writer.set(ColdpTerm.issn, csl.getISSN());
      writer.set(ColdpTerm.doi, csl.getDOI());
      writer.set(ColdpTerm.link, r.getCsl().getURL());
      writer.set(ColdpTerm.remarks, ObjectUtils.coalesce(r.getRemarks(), csl.getNote()));

      // write also to CSL-JSON file
      if (cslFirst) {
        LOG.info("Export references also as CSL-JSON");
        cslWriter = UTF8IoUtils.writerFromFile(new File(tmpDir, "reference.json"));
        cslWriter.write("[\n");
        cslFirst = false;
      } else {
        cslWriter.write(",\n");
      }
      // serialising to the writer directly will close the stream!
      cslWriter.write(ApiModule.MAPPER.writeValueAsString(csl));
    }
  }

  @Override
  void write(NameRelation rel) {
    String nameID = nameUsageKeyMap.getFirst(rel.getNameId());
    String relatedNameID = nameUsageKeyMap.getFirst(rel.getRelatedNameId());
    if (nameID == null || relatedNameID == null) {
      LOG.warn("Ignore {} NameRelation with missing nameID {} or relatedNameID {}", rel.getType(), rel.getNameId(), rel.getRelatedNameId());
    } else {
      writer.set(ColdpTerm.nameID, nameID);
      writer.set(ColdpTerm.relatedNameID, relatedNameID);
      writer.set(ColdpTerm.sourceID, sector2datasetKey(rel.getSectorKey()));
      writer.set(ColdpTerm.type, rel.getType());
      writer.set(ColdpTerm.referenceID, rel.getReferenceId());
      writer.set(ColdpTerm.remarks, rel.getRemarks());
    }
  }

  @Override
  void write(TypeMaterial tm) {
    writer.set(ColdpTerm.ID, tm.getId());
    writer.set(ColdpTerm.sourceID, sector2datasetKey(tm.getSectorKey()));
    writer.set(ColdpTerm.nameID, nameUsageKeyMap.getFirst(tm.getNameId()));
    writer.set(ColdpTerm.citation, tm.getCitation());
    writer.set(ColdpTerm.status, tm.getStatus());
    writer.set(ColdpTerm.referenceID, tm.getReferenceId());
    writer.set(ColdpTerm.country, tm.getCountry(), Country::getIso2LetterCode);
    writer.set(ColdpTerm.locality, tm.getLocality());
    writer.set(ColdpTerm.latitude, tm.getLatitude());
    writer.set(ColdpTerm.longitude, tm.getLongitude());
    writer.set(ColdpTerm.altitude, tm.getAltitude());
    writer.set(ColdpTerm.associatedSequences, tm.getAssociatedSequences());
    writer.set(ColdpTerm.host, tm.getHost());
    writer.set(ColdpTerm.sex, tm.getSex());
    writer.set(ColdpTerm.date, tm.getDate());
    writer.set(ColdpTerm.collector, tm.getCollector());
    writer.set(ColdpTerm.institutionCode, tm.getInstitutionCode());
    writer.set(ColdpTerm.catalogNumber, tm.getCatalogNumber());
    writer.set(ColdpTerm.link, tm.getLink());
    writer.set(ColdpTerm.remarks, tm.getRemarks());
  }

  @Override
  void write(TaxonConceptRelation rel) {
    writer.set(ColdpTerm.taxonID, rel.getTaxonId());
    writer.set(ColdpTerm.relatedTaxonID, rel.getRelatedTaxonId());
    writer.set(ColdpTerm.sourceID, sector2datasetKey(rel.getSectorKey()));
    writer.set(ColdpTerm.type, rel.getType());
    writer.set(ColdpTerm.referenceID, rel.getReferenceId());
    writer.set(ColdpTerm.remarks, rel.getRemarks());
  }

  @Override
  void write(String taxonID, Media m) {
    writer.set(ColdpTerm.taxonID, taxonID);
    writer.set(ColdpTerm.sourceID, sector2datasetKey(m.getSectorKey()));
    writer.set(ColdpTerm.url, m.getUrl());
    writer.set(ColdpTerm.type, m.getType());
    writer.set(ColdpTerm.format, m.getFormat());
    writer.set(ColdpTerm.title, m.getTitle());
    writer.set(ColdpTerm.created, m.getCaptured());
    writer.set(ColdpTerm.creator, m.getCapturedBy());
    writer.set(ColdpTerm.license, m.getLicense());
    writer.set(ColdpTerm.link, m.getLink());
    writer.set(ColdpTerm.remarks, m.getRemarks());
  }

  @Override
  void write(SpeciesInteraction si) {
    writer.set(ColdpTerm.taxonID, si.getTaxonId());
    writer.set(ColdpTerm.relatedTaxonID, si.getRelatedTaxonId());
    writer.set(ColdpTerm.sourceID, sector2datasetKey(si.getSectorKey()));
    writer.set(ColdpTerm.relatedTaxonScientificName, si.getRelatedTaxonScientificName());
    writer.set(ColdpTerm.type, si.getType());
    writer.set(ColdpTerm.referenceID, si.getReferenceId());
    writer.set(ColdpTerm.remarks, si.getRemarks());
  }

  @Override
  void write(String taxonID, Distribution d) {
    writer.set(ColdpTerm.taxonID, taxonID);
    writer.set(ColdpTerm.sourceID, sector2datasetKey(d.getSectorKey()));
    var area = d.getArea();
    if (area != null) {
      writer.set(ColdpTerm.area, area.getName());
      writer.set(ColdpTerm.areaID, area.getId());
      writer.set(ColdpTerm.gazetteer, area.getGazetteer());
    }
    writer.set(ColdpTerm.status, d.getStatus());
    writer.set(ColdpTerm.referenceID, d.getReferenceId());
    writer.set(ColdpTerm.remarks, d.getRemarks());
  }

  @Override
  void write(SpeciesEstimate est) {
    if (est.getTarget() != null) {
      writer.set(ColdpTerm.taxonID, est.getTarget().getId());
      //TODO: writer.set(ColdpTerm.sourceID, null);
      writer.set(ColdpTerm.estimate, est.getEstimate());
      writer.set(ColdpTerm.type, est.getType());
      writer.set(ColdpTerm.referenceID, est.getReferenceId());
      writer.set(ColdpTerm.remarks, est.getRemarks());
    }
  }

  @Override
  void writeMetadata(Dataset dataset) throws IOException {
    DatasetYamlWriter.write(dataset, new File(tmpDir, METADATA_FILENAME));
  }

  @Override
  void writeSourceMetadata(Dataset src) throws IOException {
    File f = new File(tmpDir, String.format("source/%s.yaml", src.getKey()));
    DatasetYamlWriter.write(src, f);
  }
}
