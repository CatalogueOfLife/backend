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

import life.catalogue.api.model.DOI;
import life.catalogue.coldp.ColdpTerm;

import org.apache.commons.lang3.StringUtils;
import org.catalogueoflife.coldp.gen.AbstractXlsSrcGenerator;
import org.catalogueoflife.coldp.gen.GeneratorConfig;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import com.google.common.base.Strings;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

public class Generator extends AbstractXlsSrcGenerator {
  // to be updated manually to current version !!!
  // https://talk.ictvonline.org/files/master-species-lists/
  private static final int DOWNLOAD_KEY = 12314;
  private static final URI DOWNLOAD = URI.create("http://talk.ictvonline.org/files/master-species-lists/m/msl/"+DOWNLOAD_KEY+"/download");
  // manually curated data
  private static final String ISSUED = "2021-05-18";
  private static final String VERSION = "2020.v1";
  private static final DOI SOURCE = new DOI("10.1007", "s00705-021-05156-1");
  // SPREADSHEET FORMAT
  private static final int SHEET_IDX = 2;
  private static final int SKIP_ROWS = 1;
  private static final int COL_SORT = 0;
  private static final int COL_REALM = 1;
  private static final int COL_SPECIES   = 15;
  private static final int COL_COMPOSITION = 16;
  private static final int COL_CHANGE = 17;
  private static final int COL_LINK = 20;
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
      ColdpTerm.sequenceIndex,
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
    addUsageRecord(rootID, null, null, "Viruses", null);

    Iterator<Row> iter = sheet.rowIterator();
    while (iter.hasNext()) {
      Row row = iter.next();
      if (row.getRowNum()+1 <= SKIP_ROWS) continue;

      final Integer sort = colInt(row, COL_SORT);
      final String species = col(row, COL_SPECIES);
      if (Strings.isNullOrEmpty(species)) continue;

      String parentID = writeClassification(row, sort);
      // finally the species record
      writer.set(ColdpTerm.link, link(row, COL_LINK));
      writer.set(ColdpTerm.remarks, concat(row, COL_COMPOSITION, COL_CHANGE));
      addUsageRecord(genID(Rank.SPECIES, species), parentID, Rank.SPECIES, species, sort);
    }
  }

  private String writeClassification(Row row, Integer sort) throws IOException {
    String parentID = rootID;
    int col = COL_REALM;
    for (Rank rank : CLASSIFICATION) {
      String name = col(row, col);
      if (!StringUtils.isBlank(name)) {
        String id = genID(rank, name);
        if (!ids.contains(id)) {
          addUsageRecord(id, parentID, rank, name, sort);
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

  private void addUsageRecord(String id, String parentID, Rank rank, String name, Integer sort) throws IOException {
    // create new realm record
    writer.set(ColdpTerm.ID, id);
    writer.set(ColdpTerm.parentID, parentID);
    if (rank != null) {
      writer.set(ColdpTerm.rank, rank.name());
    }
    writer.set(ColdpTerm.scientificName, name);
    writer.set(ColdpTerm.code, NomCode.VIRUS.getAcronym());
    writer.set(ColdpTerm.sequenceIndex, sort);
    writer.next();
    ids.add(id);
  }

  @Override
  protected void addMetadata() throws Exception {
    //   Walker PJ, Siddell SG, Lefkowitz EJ, Mushegian AR, Adriaenssens EM, Alfenas-Zerbini P, Davison AJ, Dempsey DM, Dutilh BE, García ML, Harrach B, Harrison RL, Hendrickson RC, Junglen S, Knowles NJ, Krupovic M, Kuhn JH, Lambert AJ, Łobocka M, Nibert ML, Oksanen HM, Orton RJ, Robertson DL, Rubino L, Sabanadzovic S, Simmonds P, Smith DB, Suzuki N, Van Dooerslaer K, Vandamme AM, Varsani A, Zerbini FM. Changes to virus taxonomy and to the International Code of Virus Classification and Nomenclature ratified by the International Committee on Taxonomy of Viruses (2021). Arch Virol. 2021 Jul 6. doi: 10.1007/s00705-021-05156-1. PMID: 34231026.
    addSource(SOURCE);
    metadata.put("issued", ISSUED);
    metadata.put("version", VERSION);
    super.addMetadata();
  }

}
