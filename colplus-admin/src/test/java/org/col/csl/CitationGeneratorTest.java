package org.col.csl;

import java.io.IOException;

import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.CslName;
import org.col.api.model.Reference;
import org.col.api.vocab.CSLRefType;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("static-method")
@Ignore
public class CitationGeneratorTest {

  @Test
  public void test01() throws IOException {
    Reference r = new Reference();
    CslData csl = new CslData();
    csl.setId(null);
    CslName author = new CslName();
    author.setFamily("Smith");
    author.setGiven("John");
    csl.setType(CSLRefType.ARTICLE_JOURNAL);
    csl.setAuthor(new CslName[]{author});
    csl.setYearSuffix("2008");
    csl.setTitle("The Art of Stuff");
    csl.setIssue("4");
    CslDate issued = new CslDate();
    issued.setDateParts(new int[][]{{2008, 8, 4}});
    csl.setIssued(issued);
    r.setCsl(csl);
    String citation = CslUtil.buildCitation(r);
    System.out.println(citation);
    assertEquals("Smith, J. (2008). The Art of Stuff, (4).", citation);
  }

  /*
   * BOOK - issue number ignored but not fatal
   */
  @Test
  public void test02() throws IOException {
    Reference r = new Reference();
    CslData csl = new CslData();
    csl.setId(null);
    CslName author = new CslName();
    author.setFamily("Smith");
    author.setGiven("John");
    csl.setType(CSLRefType.BOOK);
    csl.setAuthor(new CslName[]{author});
    csl.setYearSuffix("2008");
    csl.setTitle("The Art of Stuff");
    csl.setIssue("4");
    CslDate issued = new CslDate();
    issued.setDateParts(new int[][]{{2008, 8, 4}});
    csl.setIssued(issued);
    r.setCsl(csl);
    String citation = CslUtil.buildCitation(r);
    System.out.println(citation);
    assertEquals("Smith, J. (2008). The Art of Stuff.", citation);
  }

  /*
   * 2 authors
   */
  @Test
  public void test03() throws IOException {
    Reference r = new Reference();
    CslData csl = new CslData();
    csl.setId(null);
    CslName author1 = new CslName();
    author1.setFamily("Smith");
    author1.setGiven("John");
    CslName author2 = new CslName();
    author2.setFamily("Peterson");
    author2.setGiven("Samuel");
    csl.setType(CSLRefType.ARTICLE_JOURNAL);
    csl.setAuthor(new CslName[]{author1, author2});
    csl.setYearSuffix("2008");
    csl.setTitle("The Art of Stuff");
    csl.setIssue("4");
    CslDate issued = new CslDate();
    issued.setDateParts(new int[][]{{2008, 8, 4}});
    csl.setIssued(issued);
    r.setCsl(csl);
    String citation = CslUtil.buildCitation(r);
    System.out.println(citation);
    assertEquals("Smith, J., & Peterson, S. (2008). The Art of Stuff, (4).", citation);
  }

  /*
   * Two issue dates (doesn't do much)
   */
  @Test
  public void test04() throws IOException {
    Reference r = new Reference();
    CslData csl = new CslData();
    csl.setId(null);
    CslName author1 = new CslName();
    author1.setFamily("Smith");
    author1.setGiven("John");
    CslName author2 = new CslName();
    author2.setFamily("Peterson");
    author2.setGiven("Samuel");
    csl.setType(CSLRefType.ARTICLE_JOURNAL);
    csl.setAuthor(new CslName[]{author1, author2});
    csl.setYearSuffix("2008");
    csl.setTitle("The Art of Stuff");
    csl.setIssue("4");
    CslDate issued = new CslDate();
    issued.setDateParts(new int[][]{{2008, 8, 4}, {2008, 10, 5}});
    csl.setIssued(issued);
    r.setCsl(csl);
    String citation = CslUtil.buildCitation(r);
    System.out.println(citation);
    assertEquals("Smith, J., & Peterson, S. (2008). The Art of Stuff, (4).", citation);
  }

  @Test
  public void test05() throws IOException {
    Reference r = new Reference();
    CslData csl = new CslData();
    csl.setId(null);
    CslName author1 = new CslName();
    author1.setFamily("Smith");
    author1.setGiven("John");
    CslName author2 = new CslName();
    author2.setFamily("Peterson");
    author2.setGiven("Samuel");
    csl.setType(CSLRefType.ARTICLE_JOURNAL);
    csl.setAuthor(new CslName[]{author1, author2});
    csl.setYearSuffix("2008");
    csl.setTitle("The Art of Stuff");
    CslName editor1 = new CslName();
    editor1.setFamily("Blake");
    editor1.setGiven("Anton");
    CslName editor2 = new CslName();
    editor2.setFamily("Camus");
    editor2.setGiven("Albert");
    csl.setEditor(new CslName[]{editor1, editor2});
    csl.setIssue("4");
    CslDate issued = new CslDate();
    issued.setDateParts(new int[][]{{2008, 8, 4}, {2008, 10, 5}});
    csl.setIssued(issued);
    r.setCsl(csl);
    String citation = CslUtil.buildCitation(r);
    System.out.println(citation);
    assertEquals(
        "Smith, J., & Peterson, S. (2008). The Art of Stuff. (A. Blake & A. Camus, Eds.), (4).",
        citation);
  }

}
