package life.catalogue.resources;

import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.config.PersonConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class PersonAdminResourceTest {

  /** an inconsistent registry is a 400 before anything touches the database, and announces nothing */
  @Test
  public void inconsistentImportIsRefused() throws Exception {
    var broker = mock(EventBroker.class);
    var resource = new PersonAdminResource(null, null, broker, new PersonConfig());
    var out = new ByteArrayOutputStream();
    PersonFiles.writeZip(out, new PersonFiles.Content(List.of(),
      List.of(new PersonName("wd:Q404", "Nobody", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)), List.of()));
    var user = new User();
    user.setKey(1);
    var e = assertThrows(IllegalArgumentException.class, () -> resource.importZip(new ByteArrayInputStream(out.toByteArray()), user));
    assertTrue(e.getMessage(), e.getMessage().contains("unknown person wd:Q404"));
    verifyNoInteractions(broker);
  }
}
