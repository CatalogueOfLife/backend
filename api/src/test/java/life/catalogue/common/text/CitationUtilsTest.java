package life.catalogue.common.text;

import com.google.common.collect.Lists;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class CitationUtilsTest {

  List<Person> people(int size){
    List<Person> people = new ArrayList<>();
    while (size>0) {
      people.add(new Person("Frank", "Miller"));
      size--;
    }
    return people;
  }

  List<Person> people(String... names){
    List<Person> people = new ArrayList<>();
    Iterator<String> iter = Arrays.stream(names).iterator();
    while (iter.hasNext()) {
      String first = iter.next();
      String last = iter.hasNext() ? iter.next() : null;
      people.add(new Person(first, last));
    }
    return people;
  }

  @Test
  public void concat() {
    assertEquals("Miller F., Miller F., Miller F.", CitationUtils.concat(people(3)));
  }

  @Test
  public void concatEds() {
    assertEquals("Miller F., Miller F., Miller F. (eds.)", CitationUtils.concatEditors(people(3)));
  }

  @Test
  public void buildCitation() throws Exception {
    Dataset d = new Dataset();
    d.setKey(Datasets.DRAFT_COL);
    d.setTitle("Species 2000 & ITIS Catalogue of Life");
    d.setEditors(Person.parse(Lists.newArrayList("Roskov Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
    d.setReleased(LocalDate.parse("2019-04-21"));
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      CitationUtils.buildCitation(d)
    );

    d.setAuthors(d.getEditors());
    d.setEditors(Collections.emptyList());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      CitationUtils.buildCitation(d)
    );

    d.setEditors(null);
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      CitationUtils.buildCitation(d)
    );
  }
}