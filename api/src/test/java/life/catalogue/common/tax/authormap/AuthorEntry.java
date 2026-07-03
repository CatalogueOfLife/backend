package life.catalogue.common.tax.authormap;

import java.util.List;

public record AuthorEntry(String canonical, AuthorCode code, List<String> aliases) {}
