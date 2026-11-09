package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetDao;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;
import life.catalogue.junit.DatasetInfoCacheMockRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import jakarta.ws.rs.ForbiddenException;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The importer is no /dataset/{key} resource, so the EDITOR role alone must not let a user import or cancel
 * datasets they do not edit. See gbif/informatics#85 (B2).
 */
public class ImporterResourceTest {
  static final int MINE = 10;
  static final int OTHER = 11;

  @Rule
  public final DatasetInfoCacheMockRule infoCache = new DatasetInfoCacheMockRule()
    .put(MINE, DatasetOrigin.EXTERNAL, null)
    .put(OTHER, DatasetOrigin.EXTERNAL, null);

  ImportManager manager;
  ImporterResource resource;
  User editor;

  @Before
  public void setup() {
    manager = mock(ImportManager.class);
    resource = new ImporterResource(new WsServerConfig(), manager, mock(DatasetDao.class));
    editor = new User();
    editor.setKey(100);
    editor.addDatasetRole(User.Role.EDITOR, MINE);
  }

  @Test
  public void otherDatasetsForbidden() throws Exception {
    assertThrows(ForbiddenException.class, () -> resource.triggerImport(OTHER, false, null, editor));
    assertThrows(ForbiddenException.class, () -> resource.uploadArchive(OTHER, null, editor, null, null));
    assertThrows(ForbiddenException.class, () -> resource.uploadCsv(OTHER, null, editor, null, null));
    assertThrows(ForbiddenException.class, () -> resource.uploadXls(OTHER, null, editor, null, null));
    assertThrows(ForbiddenException.class, () -> resource.reimport(OTHER, null, editor));
    assertThrows(ForbiddenException.class, () -> resource.cancel(OTHER, editor));
    assertThrows(ForbiddenException.class, () -> resource.schedule(editor, ImportRequest.external(OTHER, editor.getKey())));
    verifyNoInteractions(manager);
  }

  @Test
  public void ownDatasetAllowed() throws Exception {
    resource.triggerImport(MINE, false, null, editor);
    resource.cancel(MINE, editor);
    verify(manager).submit(any(ImportRequest.class));
    verify(manager).cancel(MINE, editor.getKey());
  }
}
