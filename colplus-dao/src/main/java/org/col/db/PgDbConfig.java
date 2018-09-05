package org.col.db;

import java.util.Objects;
import javax.validation.constraints.NotNull;

public class PgDbConfig {
  @NotNull
  public String database = "postgres";

  @NotNull
  public String user = "postgres";

  public String password;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PgDbConfig that = (PgDbConfig) o;
    return Objects.equals(database, that.database) &&
        Objects.equals(user, that.user) &&
        Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(database, user, password);
  }

  @Override
  public String toString() {
    return "PgDbConfig{" +
        "database='" + database + '\'' +
        ", user='" + user + '\'' +
        ", password='" + password + '\'' +
        '}';
  }
}
