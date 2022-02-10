package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.TermWriter;
import life.catalogue.img.ImageService;
import life.catalogue.metadata.eml.EmlWriter;

import org.gbif.dwc.Archive;
import org.gbif.dwc.ArchiveField;
import org.gbif.dwc.ArchiveFile;
import org.gbif.dwc.MetaDescriptorWriter;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.UriBuilder;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

public class DwcaExporter extends ArchiveExporter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaExporter.class);
  private static final String EML_FILENAME = "eml.xml";
  private TermWriter writer2;
  private final Archive arch = new Archive();
  private final UriBuilder logoUriBuilder;
  private final AtomicInteger bareNameID = new AtomicInteger(1);

  public DwcaExporter(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService, Timer timer) {
    super(DataFormat.DWCA, userKey, req, factory, cfg, imageService, timer);
    logoUriBuilder = UriBuilder.fromUri(cfg.apiURI).path("/dataset/{key}/logo?size=ORIGINAL");
  }

  @Override
  protected void init(SqlSession session) throws Exception {
    super.init(session);
    additionalWriter(defineSpeciesProfile());
  }

  private void additionalWriter(Term[] terms) {
    try {
      if (writer2 != null) {
        writer2.close();
      }
      writer2 = new TermWriter.TSV(tmpDir, terms[0], List.of(Arrays.copyOfRange(terms, 1, terms.length)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  void writeMetadata(Dataset dataset) throws IOException {
    EmlWriter.write(dataset, new File(tmpDir, EML_FILENAME));
  }

  @Override
  void writeSourceMetadata(Dataset src) throws IOException {
    File f = new File(tmpDir, String.format("dataset/%s.xml", src.getKey()));
    EmlWriter.write(src, f);
  }

  @Override
  Term[] define(EntityType entity) {
    switch (entity) {
      case NAME_USAGE:
        return new Term[]{DwcTerm.Taxon, DwcTerm.taxonID,
          DwcTerm.parentNameUsageID,
          DwcTerm.acceptedNameUsageID,
          DwcTerm.originalNameUsageID,
          DwcTerm.scientificNameID,
          DwcTerm.datasetID,
          DwcTerm.taxonomicStatus,
          DwcTerm.taxonRank,
          DwcTerm.scientificName,
          DwcTerm.scientificNameAuthorship,
          DwcTerm.genericName,
          DwcTerm.infragenericEpithet,
          DwcTerm.specificEpithet,
          DwcTerm.infraspecificEpithet,
          DwcTerm.cultivarEpithet,
          DwcTerm.nameAccordingTo,
          DwcTerm.namePublishedIn,
          DwcTerm.nomenclaturalCode,
          DwcTerm.nomenclaturalStatus,
          DwcTerm.taxonRemarks,
          DcTerm.references
        };
      case VERNACULAR:
        return new Term[]{GbifTerm.VernacularName, DwcTerm.taxonID,
          DcTerm.language,
          DwcTerm.vernacularName};
      case DISTRIBUTION:
        return new Term[]{GbifTerm.Distribution, DwcTerm.taxonID,
          DwcTerm.occurrenceStatus,
          DwcTerm.locationID,
          DwcTerm.locality,
          DwcTerm.countryCode,
          DcTerm.source};
    }
    return null;
  }

  Term[] defineSpeciesProfile() {
    return new Term[]{GbifTerm.SpeciesProfile, DwcTerm.taxonID,
      GbifTerm.isExtinct,
      GbifTerm.isMarine,
      GbifTerm.isFreshwater,
      GbifTerm.isTerrestrial
    };
  }

  void write(NameUsageBase u) {
    writer.set(DwcTerm.taxonID, u.getId());
    writer.set(DwcTerm.datasetID, sector2datasetKey(u.getSectorKey()));

    if (u.isSynonym()) {
      writer.set(DwcTerm.acceptedNameUsageID, u.getParentId());
      ((Synonym)u).getAccepted().setExtinct(null); // this removes the dagger symbol from label below !!!

    } else {
      writer.set(DwcTerm.parentNameUsageID, u.getParentId());
      Taxon t = (Taxon) u;
      if (t.isExtinct() != null || (t.getEnvironments() != null && !t.getEnvironments().isEmpty())) {
        writer2.set(DwcTerm.taxonID, u.getId());
        writer2.set(GbifTerm.isExtinct, t.isExtinct());
        if (t.getEnvironments() != null && !t.getEnvironments().isEmpty()) {
          writer2.set(GbifTerm.isMarine, t.getEnvironments().contains(Environment.MARINE));
          writer2.set(GbifTerm.isFreshwater, t.getEnvironments().contains(Environment.FRESHWATER));
          writer2.set(GbifTerm.isTerrestrial, t.getEnvironments().contains(Environment.TERRESTRIAL));
        }
        try {
          writer2.next();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        t.setExtinct(null); // this removes the dagger symbol from label in write(NameUsage) !!!
      }
    }
    write((NameUsage)u);

    writer.set(DcTerm.references, u.getLink());
    writer.set(DwcTerm.nameAccordingTo, u.getAccordingTo());
  }

  void write(BareName u) {
    write((NameUsage)u);
    // GBIF validator requires the existence of taxonID for all records - we need to create an artificial, but unique key
    writer.set(DwcTerm.taxonID, "BareName-" + bareNameID.getAndIncrement());
    writer.set(DwcTerm.taxonomicStatus, "unresolved"); // maps to doubtful in GBIF
  }

  void write(NameUsage u) {
    Name n = u.getName();
    writer.set(DwcTerm.scientificNameID, n.getId());
    writer.set(DwcTerm.scientificName, u.getLabel());
    writer.set(DwcTerm.scientificNameAuthorship, n.getAuthorship());
    writer.set(DwcTerm.taxonRank, n.getRank());
    writer.set(DwcTerm.taxonomicStatus, u.getStatus());
    if (n.getPublishedInId() != null) {
      writer.set(DwcTerm.namePublishedIn, refCache.getUnchecked(n.getPublishedInId()));
    }
    if (n.getGenus() != null) {
      writer.set(DwcTerm.genericName, n.getGenus());
      writer.set(DwcTerm.infragenericEpithet, n.getInfragenericEpithet());
      writer.set(DwcTerm.specificEpithet, n.getSpecificEpithet());
      writer.set(DwcTerm.infraspecificEpithet, n.getInfraspecificEpithet());
      writer.set(DwcTerm.cultivarEpithet, n.getCultivarEpithet());
    } else if (n.getUninomial() != null && n.getRank() == Rank.GENUS) {
      writer.set(DwcTerm.genericName, n.getUninomial());
    }
    writer.set(DwcTerm.nomenclaturalCode, n.getCode(), NomCode::getAcronym);
    writer.set(DwcTerm.nomenclaturalStatus, n.getNomStatus(), NomStatus::getBotanicalLabel);

    for (NameRelation rel : nameRelMapper.listByType(n, NomRelType.BASIONYM)) {
      writer.set(DwcTerm.originalNameUsageID, rel.getRelatedNameId());
    }
    writer.set(DwcTerm.taxonRemarks, u.getRemarks());
  }

  void write(String taxonID, VernacularName vn) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DcTerm.language, vn.getLanguage());
    writer.set(DwcTerm.vernacularName, vn.getName());
  }

  void write(String taxonID, Distribution d) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DwcTerm.occurrenceStatus, d.getStatus());
    writer.set(DwcTerm.locality, d.getArea().getName());
    if (d.getArea().getGazetteer() == Gazetteer.ISO) {
        writer.set(DwcTerm.countryCode, d.getArea().getId());
    } else if (d.getArea().getGlobalId() != null) {
        writer.set(DwcTerm.locationID, d.getArea().getGlobalId());
    }
    if (d.getReferenceId() != null) {
      writer.set(DcTerm.source, refCache.getUnchecked(d.getReferenceId()));
    }
  }

  @Override
  protected void bundle() throws IOException {
    if (writer2 != null) {
      writer2.close();
    }
    addMeta();
    super.bundle();
  }

  private void addMeta() throws IOException {
    arch.setMetadataLocation(EML_FILENAME);
    arch.setCore(buildArchiveFile(EntityType.NAME_USAGE));
    for (EntityType type : List.of(EntityType.VERNACULAR, EntityType.DISTRIBUTION)) {
      arch.addExtension(buildArchiveFile(type));
    }
    arch.addExtension(buildArchiveFile(defineSpeciesProfile()));

    File metaFile = new File(tmpDir, "meta.xml");
    MetaDescriptorWriter.writeMetaFile(metaFile, arch);
  }

  private ArchiveField field(Term term, int idx) {
    ArchiveField f = new ArchiveField();
    f.setIndex(idx);
    f.setTerm(term);
    return f;
  }

  private ArchiveFile buildArchiveFile(EntityType type) {
    return buildArchiveFile(define(type));
  }

  private ArchiveFile buildArchiveFile(Term... def) {
    ArchiveFile af = ArchiveFile.buildTabFile();
    af.setArchive(arch);
    af.setEncoding("utf-8");
    af.setIgnoreHeaderLines(1);
    af.setRowType(def[0]);
    af.addLocation(TermWriter.filename(af.getRowType()));
    af.setId(field(def[1], 0));

    int idx=1;
    while (idx < def.length) {
      af.addField(field(def[idx], idx-1));
      idx++;
    }

    return af;
  }

}
