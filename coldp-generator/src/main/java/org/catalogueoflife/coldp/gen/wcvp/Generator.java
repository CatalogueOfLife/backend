package org.catalogueoflife.coldp.gen.wcvp;

import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CompressionUtil;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import life.catalogue.common.io.TermWriter;

import org.catalogueoflife.coldp.gen.AbstractGenerator;
import org.catalogueoflife.coldp.gen.GeneratorConfig;
import org.catalogueoflife.coldp.gen.TabReader;

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
  private static final int PUBLISH_AUTHOR = 13;
  private static final int PUBLISH_PLACE = 14;
  private static final int PUBLISH_VOLUME = 15;
  private static final int PUBLISH_YEAR = 16;
  private static final int NOM_REMARKS = 17;
  private static final int NAME = 21;
  private static final int AUTHOR = 22;
  private static final int ACCEPTED_ID = 23;
  private static final int ORIGINAL_ID = 24;
  private static final int COL_MIN = ORIGINAL_ID;

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
    newWriter(ColdpTerm.NameUsage, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.basionymID,
      ColdpTerm.status,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.authorship,
      ColdpTerm.family,
      ColdpTerm.genus,
      ColdpTerm.genericName,
      ColdpTerm.specificEpithet,
      ColdpTerm.infraspecificEpithet,
      ColdpTerm.nameRemarks,
      ColdpTerm.referenceID
    ));

    final var reader = TabReader.custom(
      new File(srcDir, "checklist_names.txt"), StandardCharsets.UTF_8, '|', '"', 1, COL_MIN
    );
    Set<String> all_status = new HashSet<>();
    Set<String> all_ranks = new HashSet<>();
    for (var row : reader) {
      writer.set(ColdpTerm.ID, row[ID]);
      String status = row[STATUS];
      boolean accepted = status.equalsIgnoreCase("Accepted");
      writer.set(ColdpTerm.status, status);
      all_status.add(status);
      writer.set(ColdpTerm.rank, row[RANK]);
      all_ranks.add(row[RANK]);
      writer.set(ColdpTerm.scientificName, row[NAME]);
      writer.set(ColdpTerm.authorship, row[AUTHOR]);
      writer.set(ColdpTerm.genericName, row[GENUS]);
      writer.set(ColdpTerm.specificEpithet, row[SPECIES]);
      writer.set(ColdpTerm.infraspecificEpithet, row[INFRASPECIES]);
      writer.set(ColdpTerm.nameRemarks, row[NOM_REMARKS]);
      writer.set(ColdpTerm.basionymID, row[ORIGINAL_ID]);
      writer.set(ColdpTerm.family, row[FAMILY]);
      if (accepted) {
        writer.set(ColdpTerm.genus, row[GENUS]);
      } else {
        writer.set(ColdpTerm.parentID, row[ACCEPTED_ID]);
      }
      //TODO: write refs
      //writer.set(ColdpTerm.referenceID, null);
      writer.next();
      if (reader.getContext().currentLine()>100) break;
    }

    System.out.println("\n\nDistinct ranks:");
    all_ranks.forEach(System.out::println);
    System.out.println("\n\nDistinct status:");
    all_status.forEach(System.out::println);
  }
}
