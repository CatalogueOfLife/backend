package life.catalogue.importer.reference;

import life.catalogue.api.model.Reference;

public interface ReferenceStore {

  Reference get(String key);

  Reference refByCitation(String citation);

  static ReferenceStore passThru() {
    return new ReferenceStore() {

      @Override
      public Reference get(String key) {
        return null;
      }

      @Override
      public Reference refByCitation(String citation) {
        return null;
      }
    };
  }

}
