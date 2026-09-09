package life.catalogue.common.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The aggregate checksum over a set of files, used to tell whether the data files of a source archive changed.
 */
public class ChecksumUtilsFilesTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private Path dir;

  @Before
  public void init() {
    dir = tmp.getRoot().toPath();
  }

  private Path write(String name, String content) throws IOException {
    Path f = dir.resolve(name);
    Files.createDirectories(f.getParent());
    Files.writeString(f, content, StandardCharsets.UTF_8);
    return f;
  }

  @Test
  public void independentOfTheOrderTheFilesAreGivenIn() throws Exception {
    Path a = write("Name.tsv", "ID\tname\n1\tAbies\n");
    Path b = write("Taxon.tsv", "ID\tparentID\n1\t\n");

    assertEquals(ChecksumUtils.getMD5Checksum(dir, List.of(a, b)),
                 ChecksumUtils.getMD5Checksum(dir, List.of(b, a)));
  }

  @Test
  public void changesWhenAFilesContentChanges() throws Exception {
    Path a = write("Name.tsv", "ID\tname\n1\tAbies\n");
    Path b = write("Taxon.tsv", "ID\tparentID\n1\t\n");
    String before = ChecksumUtils.getMD5Checksum(dir, List.of(a, b));

    write("Taxon.tsv", "ID\tparentID\n1\t\n2\t1\n");

    assertNotEquals(before, ChecksumUtils.getMD5Checksum(dir, List.of(a, b)));
  }

  @Test
  public void changesWhenAFileIsRenamed() throws Exception {
    Path a = write("Name.tsv", "ID\tname\n1\tAbies\n");
    String before = ChecksumUtils.getMD5Checksum(dir, List.of(a));

    Path renamed = write("names.tsv", "ID\tname\n1\tAbies\n");

    assertNotEquals(before, ChecksumUtils.getMD5Checksum(dir, List.of(renamed)));
  }

  @Test
  public void changesWhenAFileIsAdded() throws Exception {
    Path a = write("Name.tsv", "ID\tname\n1\tAbies\n");
    String before = ChecksumUtils.getMD5Checksum(dir, List.of(a));

    Path b = write("Taxon.tsv", "ID\tparentID\n1\t\n");

    assertNotEquals(before, ChecksumUtils.getMD5Checksum(dir, List.of(a, b)));
  }

  @Test
  public void ignoresWhereTheFolderItselfLives() throws Exception {
    Path a = write("Name.tsv", "ID\tname\n1\tAbies\n");
    String here = ChecksumUtils.getMD5Checksum(dir, List.of(a));

    Path other = Files.createDirectory(dir.resolve("elsewhere"));
    Path moved = other.resolve("Name.tsv");
    Files.writeString(moved, "ID\tname\n1\tAbies\n", StandardCharsets.UTF_8);

    assertEquals(here, ChecksumUtils.getMD5Checksum(other, List.of(moved)));
  }

  @Test
  public void keepsSubfolderPathsApart() throws Exception {
    Path root = write("Name.tsv", "ID\tname\n1\tAbies\n");
    Path nested = write("data/Name.tsv", "ID\tname\n1\tAbies\n");

    assertNotEquals(ChecksumUtils.getMD5Checksum(dir, List.of(root)),
                    ChecksumUtils.getMD5Checksum(dir, List.of(nested)));
  }

  @Test
  public void emptyFileListHasAChecksum() throws Exception {
    assertEquals(32, ChecksumUtils.getMD5Checksum(dir, List.of()).length());
  }
}
