package life.catalogue.common.tax.authormap;

import java.util.List;

public interface AuthorSource {
  String name();
  List<AuthorEntry> read() throws Exception;
}
