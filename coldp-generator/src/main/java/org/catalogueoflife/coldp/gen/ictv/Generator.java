/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.catalogueoflife.coldp.gen.ictv;

import life.catalogue.coldp.ColdpTerm;

import life.catalogue.common.io.TermWriter;

import org.catalogueoflife.coldp.gen.AbstractXlsSrcGenerator;
import org.catalogueoflife.coldp.gen.GeneratorConfig;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import com.google.common.base.Strings;

import org.gbif.nameparser.api.Rank;

public class Generator extends AbstractXlsSrcGenerator {
  // to be updated manually to current version !!!
  // https://talk.ictvonline.org/files/master-species-lists/
  private static final int DOWNLOAD_KEY = 12314;
  private static final String PUBDATE = "2021-05-18";
  private static final String VERSION = "2020.v1";
  private static final URI DOWNLOAD = URI.create("http://talk.ictvonline.org/files/master-species-lists/m/msl/"+DOWNLOAD_KEY+"/download");

  // metadata
  private static final String ORG = " International Committee on Taxonomy of Viruses (ICTV)";
  private static final String CONTACT_EMAIL = "info@ictvonline.org";
  private static final String NOM_CODE = "ICTV";

  // SPREADSHEET FORMAT
  private static final int SHEET_IDX = 2;
  private static final int SKIP_ROWS = 1;
  //	Realm	Subrealm	Kingdom	Subkingdom	Phylum	Subphylum	Class	Subclass	Order	Suborder	Family	Subfamily	Genus	Subgenus	Species	Type Species?	Genome Composition	Last Change	MSL of Last Change	Proposal for Last Change 	Taxon History URL
  private static final int COL_REALM = 1;
  private static final List<Rank> CLASSIFICATION = List.of(
    Rank.REALM,
    Rank.SUBREALM,
    Rank.KINGDOM,
    Rank.SUBKINGDOM,
    Rank.PHYLUM,
    Rank.SUBPHYLUM,
    Rank.CLASS,
    Rank.SUBCLASS,
    Rank.ORDER,
    Rank.SUBORDER,
    Rank.FAMILY,
    Rank.SUBFAMILY,
    Rank.GENUS,
    Rank.SUBGENUS
  );

  private static final int COL_SPECIES   = 15;
  private static final int COL_COMPOSITION = 16;
  private static final int COL_LINK = 20;

  private final String rootID = "root";
  private final Set<String> ids = new HashSet<>();

  public Generator(GeneratorConfig cfg) throws IOException {
    super(cfg, true, DOWNLOAD);
  }

  @Override
  protected void addData() throws Exception {

    // write just the NameUsage file
    newWriter(ColdpTerm.NameUsage, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.code,
      ColdpTerm.link,
      ColdpTerm.remarks
    ));

    Sheet sheet = wb.getSheetAt(SHEET_IDX);
    int rows = sheet.getPhysicalNumberOfRows();
    LOG.info("{} rows found in excel sheet", rows);

    // first add a single root
    addUsageRecord(rootID, null, null, "Viruses");

    Iterator<Row> iter = sheet.rowIterator();
    while (iter.hasNext()) {
      Row row = iter.next();
      if (row.getRowNum()+1 <= SKIP_ROWS) continue;

      final String species = col(row, COL_SPECIES);
      if (Strings.isNullOrEmpty(species)) continue;

      String parentID = writeClassification(row);
      // finally the species record
      writer.set(ColdpTerm.link, link(row, COL_LINK));
      writer.set(ColdpTerm.remarks, col(row, COL_COMPOSITION));
      addUsageRecord(genID(Rank.SPECIES, species), parentID, Rank.SPECIES, species);
    }
  }


  private String writeClassification(Row row) throws IOException {
    String parentID = rootID;
    int col = COL_REALM;
    for (Rank rank : CLASSIFICATION) {
      String name = col(row, col);
      if (name != null) {
        String id = genID(rank, name);
        if (!ids.contains(id)) {
          addUsageRecord(id, parentID, rank, name);
        }
        parentID = id;
      }
      col++;
    }
    return parentID;
  }

  private static String genID(Rank rank, String name) {
    return rank.name().toLowerCase() + ":" + name.toLowerCase().trim().replace(" ", "_");
  }

  private void addUsageRecord(String id, String parentID, Rank rank, String name) throws IOException {
    // create new realm record
    writer.set(ColdpTerm.ID, id);
    writer.set(ColdpTerm.parentID, parentID);
    if (rank != null) {
      writer.set(ColdpTerm.rank, rank.name());
    }
    writer.set(ColdpTerm.scientificName, name);
    writer.set(ColdpTerm.code, NOM_CODE);
    writer.next();
    ids.add(id);
  }

  private boolean toBool(String x) {
    return x != null && x.trim().equals("1");
  }

  @Override
  protected void addMetadata() {
//    // metadata
//    dataset.setTitle("ICTV Master Species List " + VERSION);
//    dataset.setDescription("Official lists of all ICTV-approved taxa.\n" +
//        "\n" +
//        "The creation or elimination, (re)naming, and (re)assignment of a virus species, genus, (sub)family, or order are all taxonomic acts that require public scrutiny and debate, leading to formal approval by the full membership of the ICTV. " +
//        "In contrast, the naming of a virus isolate and its assignment to a pre-existing species are not considered taxonomic acts and therefore do not require formal ICTV approval. " +
//        "Instead they will typically be accomplished by publication of a paper describing the virus isolate in the peer-reviewed virology literature.\n" +
//        "\n" +
//        "Descriptions of virus satellites, viroids and the agents of spongiform encephalopathies (prions) of humans and several animal and fungal species are included.\n"
//    );
//    dataset.setHomepage(uri("https://talk.ictvonline.org/taxonomy/w/ictv-taxonomy"));
//    dataset.setLogoUrl(uri("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/ictv/ictv-logo.png"));
//    setPubDate(PUBDATE);
//    addExternalData(DOWNLOAD, null);
//    addContact(ORG, CONTACT_EMAIL);
  }
}
