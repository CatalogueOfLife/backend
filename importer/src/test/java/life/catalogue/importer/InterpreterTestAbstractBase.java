package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.ReferenceMapStore;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public abstract class InterpreterTestAbstractBase<I extends InterpreterBase> {
  
  @Mock
  ReferenceMapStore refStore;

  @Mock
  protected ImportStore store;

  protected IssueContainer issues = new IssueContainer.Simple();
  protected I interpreter;

  @Before
  public void init() {
    when(store.references()).thenReturn(refStore);
    interpreter = buildInterpreter(new DatasetSettings(), new ReferenceFactory(1, refStore, null), store);
  }

  protected abstract I buildInterpreter(DatasetSettings settings, ReferenceFactory refFactory, ImportStore store);

}
