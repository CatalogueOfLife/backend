package org.catalogueoflife.coldp.gen.wcvp;

import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.io.CompressionUtil;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import life.catalogue.common.io.TabReader;

import org.apache.commons.lang3.StringUtils;
import org.catalogueoflife.coldp.gen.AbstractGenerator;
import org.catalogueoflife.coldp.gen.GeneratorConfig;

public class Generator extends AbstractGenerator {
  private static final URI DOWNLOAD = URI.create("https://storage.googleapis.com/kew-dev-backup/world_checklist_names_and_distribution_feb_21.zip");

  // plant_name_id|ipni_id|taxon_rank|taxon_status|family|genus_hybrid|genus|species_hybrid|species|infraspecific_rank|infraspecies|parenthetical_author|primary_author|publication_author|place_of_publication|volume_and_page|first_published|nomenclatural_remarks|geographic_area|lifeform_description|climate_description|taxon_name|taxon_authors|accepted_plant_name_id|basionym_plant_name_id|homotypic_synonym
  // 617796-az|44936-1|Species|Accepted|Acanthaceae||Achyrocalyx||decaryi||||Benoist||Bull. Soc. Bot. France|76: 1037|(1929 publ. 1930)||Madagascar||Seasonally Dry Tropical|Achyrocalyx decaryi|Benoist|617796-az||
  // 615307-az|44927-1|Species|Synonym|Acanthaceae||Acanthus||ugandensis||||C.B.Clarke||J. Linn. Soc., Bot.|37: 527|(1906)||Trop. Africa||Seasonally Dry Tropical|Acanthus ugandensis|C.B.Clarke|615286-az||
  // 621163-az|19963-1|Genus|Unplaced|Acanthaceae||Adelaida||||||Buc'hoz||Herb. Color. Amérique|: t. 74|(1783)|||||Adelaida|Buc'hoz|||
  private static final int ID = 0;
  private static final int IPNI_ID = 1;
  private static final int RANK = 2;
  private static final int STATUS = 3;
  private static final int FAMILY = 4;
  private static final int GENUS = 6;
  private static final int SPECIES = 8;
  private static final int INFRASPECIES = 10;
  private static final int PRIMARY_AUTHOR = 12;
  private static final int PUBLISH_AUTHOR = 13;
  private static final int PUBLISH_PLACE = 14;
  private static final int PUBLISH_VOLUME = 15;
  private static final int PUBLISH_YEAR = 16;
  private static final int NOM_REMARKS = 17;
  private static final int NAME = 21;
  private static final int AUTHOR = 22;
  private static final int ACCEPTED_ID = 23;
  private static final int ORIGINAL_ID = 24;
  private static final int USAGE_MIN = ORIGINAL_ID;

  // plant_name_id|continent_code_l1|continent|region_code_l2|region|area_code_l3|area|introduced|extinct|location_doubtful
  // 329042-az|7|NORTHERN AMERICA|79|Mexico|MXN|Mexico Northwest|0|0|0
  private static final int TAXON_ID = 0;
  private static final int AREA_ID = 5;
  private static final int AREA = 6;
  private static final int DIST_MIN = AREA;

  private static final Pattern BRACKET_YEAR = Pattern.compile("\\(?([^()]+)\\)?");

  public Generator(GeneratorConfig cfg) {
    super(cfg, true);
  }

  @Override
  protected void addData() throws Exception {
    // get latest CSVs
    File zip = new File("/tmp/wcvp.zip");
    if (!zip.exists()) {
      LOG.info("Downloading latest data from {}", DOWNLOAD);
      download.download(DOWNLOAD, zip);
    } else {
      LOG.warn("Reuse data from {}", zip);
    }
    // unzip
    File srcDir = new File("/tmp/wcvp");
    if (srcDir.exists()) {
      org.apache.commons.io.FileUtils.cleanDirectory(srcDir);
    } else {
      srcDir.mkdirs();
    }
    CompressionUtil.unzipFile(srcDir, zip);

    // read & write core file
    initRefWriter(List.of(
      ColdpTerm.ID,
      ColdpTerm.author,
      ColdpTerm.containerTitle,
      ColdpTerm.issued,
      ColdpTerm.volume,
      ColdpTerm.issue,
      ColdpTerm.page
    ));
    newWriter(ColdpTerm.NameUsage, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.basionymID,
      ColdpTerm.status,
      ColdpTerm.nameStatus,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.authorship,
      ColdpTerm.family,
      ColdpTerm.genus,
      //ColdpTerm.genericName,
      //ColdpTerm.specificEpithet,
      //ColdpTerm.infraspecificEpithet,
      ColdpTerm.nameRemarks,
      ColdpTerm.nameReferenceID
    ));

    var reader = TabReader.custom(
      new File(srcDir, "checklist_names.txt"), StandardCharsets.UTF_8, '|', '"', 1, USAGE_MIN
    );
    for (var row : reader) {
      final String id = row[ID];
      writer.set(ColdpTerm.ID, id);
      String status = row[STATUS];
      writer.set(ColdpTerm.status, status != null && status.equalsIgnoreCase("Unplaced") ? "bare name" : status);
      writer.set(ColdpTerm.rank, row[RANK]);
      writer.set(ColdpTerm.scientificName, row[NAME]);
      writer.set(ColdpTerm.authorship, row[AUTHOR]);
      // don't use these, as they lack hybrid markers and when supplied take precedence over the full scientificName
      //writer.set(ColdpTerm.genericName, row[GENUS]);
      //writer.set(ColdpTerm.specificEpithet, row[SPECIES]);
      //writer.set(ColdpTerm.infraspecificEpithet, row[INFRASPECIES]);
      writer.set(ColdpTerm.nameRemarks, stripLeadingComma(row[NOM_REMARKS]));
      writer.set(ColdpTerm.basionymID, row[ORIGINAL_ID]);
      writer.set(ColdpTerm.parentID, row[ACCEPTED_ID]);
      if (status != null) {
        switch (status) {
          case "Accepted":
            writer.set(ColdpTerm.family, row[FAMILY]);
            writer.set(ColdpTerm.genus, row[GENUS]);
            writer.unset(ColdpTerm.parentID);
            break;
          case "Illegitimate":
          case "Invalid":
          case "Orthographic":
            writer.set(ColdpTerm.status, TaxonomicStatus.SYNONYM);
            writer.set(ColdpTerm.nameStatus, status);
            break;
          default:
        }
      }

      // construct reference
      if (row[PUBLISH_PLACE] != null) {
        // publication author is only given when it differs from the primary author
        refWriter.set(ColdpTerm.author, ObjectUtils.coalesce(row[PUBLISH_AUTHOR], row[PRIMARY_AUTHOR]));
        refWriter.set(ColdpTerm.containerTitle, row[PUBLISH_PLACE]);
        String rawYear = row[PUBLISH_YEAR];
        if (rawYear != null) {
          var m = BRACKET_YEAR.matcher(rawYear);
          if (m.find()) {
            refWriter.set(ColdpTerm.issued, m.group(1));
          } else {
            LOG.warn("Failed to parse publishing year: {}", rawYear);
          }
        }
        CslUtil.parseVolumeIssuePage(row[PUBLISH_VOLUME]).ifPresent(vip -> {
          refWriter.set(ColdpTerm.volume, vip.volume);
          refWriter.set(ColdpTerm.issue, vip.issue);
          refWriter.set(ColdpTerm.page, vip.page);
        });
        String rid = nextRef();
        writer.set(ColdpTerm.nameReferenceID, rid);
      }

      writer.next();
    }

    // Distribution
    reader = TabReader.custom(
      new File(srcDir, "checklist_distribution.txt"), StandardCharsets.UTF_8, '|', '"', 1, DIST_MIN
    );
    newWriter(ColdpTerm.Distribution, List.of(
      ColdpTerm.taxonID,
      ColdpTerm.gazetteer,
      ColdpTerm.areaID,
      ColdpTerm.area
    ));
    for (var row : reader) {
      writer.set(ColdpTerm.taxonID, row[TAXON_ID]);
      writer.set(ColdpTerm.areaID, row[AREA_ID]);
      writer.set(ColdpTerm.area, row[AREA]);
      writer.set(ColdpTerm.gazetteer, Gazetteer.TDWG);
      writer.next();
    }
  }

  private String stripLeadingComma(String x) {
    if (x != null) {
      return StringUtils.stripStart(x.trim(), ",");
    }
    return null;
  }
}
