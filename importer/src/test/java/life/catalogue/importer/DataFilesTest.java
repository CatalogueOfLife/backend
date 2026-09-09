package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The data files of an unpacked source folder - what a source data checksum is taken over.
 * Metadata is deliberately not part of it, so that a metadata only change is visible as such.
 */
public class DataFilesTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static List<String> list(String resource, DataFormat format) throws IOException {
    Path folder = Resources.toPath(resource);
    return relative(folder, DataFiles.list(folder, format));
  }

  private static List<String> relative(Path folder, List<Path> files) {
    return files.stream()
                .map(p -> folder.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString())
                .collect(Collectors.toList());
  }

  @Test
  public void coldp() throws Exception {
    assertEquals(List.of(
      "data/distribution.tsv",
      "data/media.tsv",
      "data/name-relation.tsv",
      "data/name.tsv",
      "data/reference.tsv",
      "data/species-estimate.tsv",
      "data/species-interaction.tsv",
      "data/synonym.tsv",
      "data/taxon-concept-relation.tsv",
      "data/taxon.tsv",
      "data/treatments/Hind2013.txt",
      "data/treatments/Jarvis2007.html",
      "data/type-material.tsv",
      "data/vernacular-name.tsv"
    ), list("coldp/0", DataFormat.COLDP));
  }

  @Test
  public void coldpReferenceFilesAreData() throws Exception {
    assertEquals(List.of(
      "name.tsv",
      "reference.bib",
      "reference.json",
      "reference.tsv"
    ), list("coldp/bibtex", DataFormat.COLDP));
  }

  @Test
  public void coldpDefaultsChangeInterpretationSoTheyAreData() throws Exception {
    Path dir = tmp.getRoot().toPath();
    Files.writeString(dir.resolve("Name.tsv"), "ID\tscientificName\n1\tAbies alba\n", StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("metadata.yaml"), "title: Test\n", StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("default.yaml"), "Name:\n  code: botanical\n", StandardCharsets.UTF_8);

    assertEquals(List.of("Name.tsv", "default.yaml"), relative(dir, DataFiles.list(dir, DataFormat.COLDP)));
  }

  @Test
  public void dwcaMetaXmlIsDataButEmlIsNot() throws Exception {
    assertEquals(List.of("meta.xml", "taxon.txt"), list("dwca/25", DataFormat.DWCA));
  }

  @Test
  public void dwcaListsEveryMappedFile() throws Exception {
    var files = list("dwca/44", DataFormat.DWCA);
    assertTrue(files.toString(), files.contains("meta.xml"));
    assertTrue(files.toString(), files.contains("wcvp_taxon.csv"));
    assertTrue(files.toString(), files.contains("wcvp_distribution.csv"));
    assertFalse(files.toString(), files.contains("eml.xml"));
    assertFalse(files.toString(), files.contains("README.md"));
  }

  /**
   * ACEF carries its metadata as a data row in SourceDatabase, which is excluded like any other metadata.
   */
  @Test
  public void acefExcludesSourceDatabase() throws Exception {
    assertEquals(List.of(
      "AcceptedInfraspecificTaxa.txt",
      "AcceptedSpecies.txt",
      "CommonNames.txt",
      "Distribution.txt",
      "NameReferencesLinks.txt",
      "References.txt",
      "Synonyms.txt"
    ), list("acef/0", DataFormat.ACEF));
  }

  @Test
  public void txtree() throws Exception {
    assertEquals(List.of("mammalia.tree"), list("txtree/0", DataFormat.TEXT_TREE));
  }

  @Test
  public void sortedByRelativePath() throws Exception {
    var files = list("coldp/0", DataFormat.COLDP);
    assertEquals(files.stream().sorted().collect(Collectors.toList()), files);
  }
}
