An `acceptedNameUsage` given only as a name string, so the inserter has to materialize it as an
implicit usage. The string `Pic?ea abies` is silently repaired to `Picea abies` by the name parser,
which reports it only as a `QUESTION_MARKS_REMOVED` warning - the created usage must keep that issue.
