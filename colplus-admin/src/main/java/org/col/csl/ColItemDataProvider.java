package org.col.csl;

import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import org.col.api.model.CslData;

public class ColItemDataProvider implements ItemDataProvider {

  private CslData cslData;

  @Override
  public CSLItemData retrieveItem(String id) {
    return CslDataConverter.toCSLItemData(cslData);
  }

  @Override
  public String[] getIds() {
    return new String[] {cslData.getId()};
  }

  public void setCslData(CslData cslData) {
    this.cslData = cslData;
  }

}
