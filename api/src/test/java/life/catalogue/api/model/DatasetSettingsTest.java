package life.catalogue.api.model;

import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Setting;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class DatasetSettingsTest extends SerdeTestBase<DatasetSettings> {

  public DatasetSettingsTest() {
    super(DatasetSettings.class);
  }
  
  @Override
  public DatasetSettings genTestValue() throws Exception {
    return generateTestValue();
  }

  public static DatasetSettings generateTestValue() {
    DatasetSettings ds = DatasetSettings.of(Map.of(
      Setting.DISTRIBUTION_GAZETTEER, Gazetteer.ISO,
      Setting.REMATCH_DECISIONS, true,
      Setting.CSV_DELIMITER, "\t",
      Setting.CSV_QUOTE, "\"",
      Setting.CSV_QUOTE_ESCAPE, "\\",
      Setting.NOMENCLATURAL_CODE, NomCode.BOTANICAL,
      Setting.IMPORT_FREQUENCY, Frequency.MONTHLY,
      Setting.DATA_ACCESS, URI.create("www.gbif.org"),
      Setting.SECTOR_ENTITIES, List.of(EntityType.VERNACULAR),
      Setting.SECTOR_RANKS, List.of(Rank.GENUS, Rank.SPECIES, Rank.SUBGENUS, Rank.TRIBE)
    ));
    return ds;
  }

  @Test
  public void testSettings() throws Exception {
    DatasetSettings d = new DatasetSettings();

    assertNull(d.getEnum(Setting.NOMENCLATURAL_CODE));

    d.put(Setting.NOMENCLATURAL_CODE, NomCode.BOTANICAL);
    assertEquals(NomCode.BOTANICAL, d.getEnum(Setting.NOMENCLATURAL_CODE));
  }

}