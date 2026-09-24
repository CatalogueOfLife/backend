# Person Author Comparison, Phase 3: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A second author matcher that compares authors as persons of the registry, measured against the string matcher
on the re-parsed corpus and on a hand curated fixture of relatives, with the numbers in the design record's Outcome.

**Architecture:** First the registry gets the fixes the final review of phase 2 deferred and the evaluation needs:
- several ids of one authority per person;
- a family name with its suffix as a derived form;
- alternative family names no longer joined;
- initials from variants too.

The registry is then regenerated. The corpus pairs carry the taxonomic group their names were classified under, and the
corpus report takes the comparator it measures. `PersonResolver` resolves a citation to candidate persons, narrowed by the
year and group of the name. `PersonAuthorMatcher` decides author pairs from those candidates: a shared person, unrelated
persons, relatives, or the string fallback. `PersonCorpusReport` runs the corpus report under both relatives policies,
explains which rule decided the verdicts and diffs them against the string verdicts.

**Tech Stack:** Java 25, JUnit 4, Maven. Modules `dao` (corpus tools in test scope) and `core` (registry, resolver,
matcher in main; harvest, fixture, person report in test scope).

**Spec:** `docs/2026-09-23-person-author-comparison.md`, sections "4. How the person matcher decides", "5. Evaluation"
and "Phases" (phase 3). Phase 2 deferred minors are in its Outcome ("Left for later").

## Global Constraints

- Work in the worktree `/Users/markus/code/col/backend/.claude/worktrees/author-corpus` on branch
  `feat/person-author-comparison`, one branch off master, never stacked. Every shell needs
  `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca`.
- Single test classes run as `mvn -o -pl <module> test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`, without
  `-q`. `-pl core` takes `api` and `dao`, including dao's `tests` jar with the corpus tools, from `~/.m2`: after changing
  `api` or `dao` (main or test) run `mvn -o -q -pl dao -am install -DskipTests` before any `-pl core` build.
- The string matcher and `AuthorComparator` stay unchanged. The string report on the same pairs must give the same
  verdicts before and after this phase: `AuthorVerdictDiff` 0 flips.
- The person matcher is not wired into production (`UsageMatcher`, `TreeMergeHandler`, `NameIdentity`). That is decided
  from the numbers, later.
- Registry, resolver and matcher live in `core/src/main/java/life/catalogue/matching/person/`; harvest in
  `core/src/test/java/life/catalogue/matching/person/harvest/`; corpus tools in
  `dao/src/test/java/life/catalogue/matching/authorship/corpus/`.
- Narrowing (spec): a person is ruled out when the name's year lies before `born + minAge` or after `died + posthumous`;
  active years do the same without life dates; a person whose groups are all disparate to the name's group
  (`TaxGroup.isDisparateTo`) is ruled out; a person without years or groups is never ruled out; a citation whose
  candidates are all ruled out counts as unresolved. `minAge` is 10.
- Deciding (spec): both resolved and overlapping: `EQUAL`; both resolved, disjoint, unrelated: `DIFFERENT`; disjoint but
  related by `PARENT` or `SIBLING`: the relatives policy, `UNKNOWN` by default or `DIFFERENT`; one side unresolved: the
  string cascade with the author map, the citation compared against every form of the other side's persons. Teams keep
  the rule of the string matcher: any author pair `EQUAL` makes the teams `EQUAL`.
- "Better" (spec): weighted by names, no worse than the string matcher on `SAME` judged `EQUAL` and on `DIFF` judged
  `DIFFERENT` of the re-parsed corpus; fewer relatives fixture pairs judged `EQUAL` than the string matcher, reported per
  policy; every regressed pair backed by 10 or more names reviewed by hand; run time and heap with the registry loaded.
- Style: 2 space indent, 140 columns, `javax.annotation.Nullable`, comments explain why. Every commit message ends with
  `Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg`.

## Review Focus

- The comparator hands the matcher authors already normalized (`AuthorshipNormalizer.normalize`). They must resolve like
  the citation they came from (pinned in Task 6).
- Years come as the parser gives them: `1753`, `1878 [1879]`, `184?`, none. An imprecise or absent year must narrow
  nothing and never crash (pinned in Task 6).
- A citation whose candidates are all ruled out by year or group is unresolved and goes to the string fallback, never
  `EQUAL` through an empty overlap (pinned in Task 7).
- Pairs files written before the `group` column and exports without `classification` still read, including the
  committed test fixtures (pinned in Task 4).
- The comparator combines the author verdict with the years by `Equality.and`, where `EQUAL.and(UNKNOWN)` is `EQUAL`,
  and turns an `UNKNOWN` in a year conflict into `DIFFERENT`. Under the `UNKNOWN` policy relatives are therefore `EQUAL`
  with agreeing years, `DIFFERENT` with years a few apart and `UNKNOWN` without years. The matcher must not work around
  that; the reports must show it (pinned in Task 7).

---

### Task 1: Several ids of one authority per person

The review of phase 2 found 179 Wikidata items with more than one IPNI id (IPNI's duplicate records of one author) and
1,279 with more than one ZooBank id. The harvest kept the first, and 160 of the other IPNI ids became persons of their own.
An extra id becomes a former id of the person, so it resolves to it, and a person of the files holding it is joined.

**Files:**
- Modify: `core/src/test/java/life/catalogue/matching/person/harvest/PersonRecord.java`
- Modify: `core/src/test/java/life/catalogue/matching/person/harvest/WikidataPersonSource.java:127-132`
- Modify: `core/src/test/java/life/catalogue/matching/person/harvest/PersonMerger.java`
- Modify: `docs/AUTHOR-PERSONS.md` (the `id` bullet)
- Test: `core/src/test/java/life/catalogue/matching/person/harvest/WikidataPersonSourceTest.java`,
  `core/src/test/java/life/catalogue/matching/person/harvest/PersonMergerTest.java`

**Interfaces:**
- Produces: `PersonRecord.otherIds()` (`Set<String>` of prefixed ids, insertion ordered), `PersonRecord.Builder.otherId(String)`.

- [ ] **Step 1: Write the failing tests**

In `WikidataPersonSourceTest`:

```java
  /** an item with two IPNI or ZooBank ids: the first is the person's, the others are the same person recorded twice */
  @Test
  public void severalIdsOfOneAuthority() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q2", "1-1"), id("Q2", "2-2")), WikidataPersonSource.IdProperty.P586, persons);
    WikidataPersonSource.addIds(rows(id("Q2", "abc"), id("Q2", "def")), WikidataPersonSource.IdProperty.P2006, persons);
    persons.get("Q2").label("Anna Smith");
    PersonRecord r = persons.get("Q2").build();
    assertEquals("1-1", r.ipni());
    assertEquals("ABC", r.zoobank());
    assertEquals(Set.of("ipni:2-2", "zb:DEF"), r.otherIds());
  }
```

In `PersonMergerTest`:

```java
  /** a second IPNI id of an item is a former id of its person, and the IPNI author of that id joins it */
  @Test
  public void secondIpniIdOfAnItem() {
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var i1 = ipni("1-1");
    i1.name("A.Sm.", NameKind.STANDARD, FormCode.BOT);
    var i2 = ipni("2-2");
    i2.name("A.Smith", NameKind.STANDARD, FormCode.BOT);
    var r = merge(PersonFiles.Content.empty(), w.build(), i1.build(), i2.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals("1-1", p.ipni());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertTrue(r.content().names().contains(new PersonName("wd:Q1", "A.Smith", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)));
    assertEquals(List.of(), r.report().ambiguous);
    assertEquals(List.of(), r.report().conflicts);
  }

  /** the files hold the second IPNI id as a person of its own, as the first harvest wrote 160 of them: the item joins it */
  @Test
  public void secondIpniIdJoinsAPersonOfTheFiles() {
    var existing = new PersonFiles.Content(
      List.of(new Person("ipni:2-2", null, "2-2", null, List.of(), "Smith", "Anna", null, null, null, null, null, Set.of(), Provenance.IPNI)),
      List.of(new PersonName("ipni:2-2", "A.Smith", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI)),
      List.of());
    var w = wd("Q1");
    w.ipni = "1-1";
    w.otherId(Person.IPNI + "2-2");
    w.label("Anna Smith");
    var r = merge(existing, w.build());
    assertEquals(1, r.content().persons().size());
    Person p = r.content().persons().get(0);
    assertEquals("wd:Q1", p.id());
    assertEquals(List.of("ipni:2-2"), p.formerIds());
    assertEquals("Smith", p.family());
    assertEquals(List.of(), r.report().conflicts);
  }

  /** a second id another item holds as its own stays with that item: reported, never a former id of two persons */
  @Test
  public void secondIdOfAnotherItemIsReported() {
    var a = wd("Q1");
    a.ipni = "2-2";
    a.label("Anna Smith");
    var b = wd("Q2");
    b.ipni = "1-1";
    b.otherId(Person.IPNI + "2-2");
    b.label("Anne Smith");
    var r = merge(PersonFiles.Content.empty(), a.build(), b.build());
    assertEquals(2, r.content().persons().size());
    assertEquals(List.of(), person(r.content(), "wd:Q2").formerIds());
    assertEquals(1, r.report().ambiguous.size());
  }
```

`merge()` already checks `PersonRegistry.problems()` is empty, which catches an id held by two persons.

- [ ] **Step 2: Run the tests to see them fail**

Run: `mvn -o -pl core test -Dtest='WikidataPersonSourceTest,PersonMergerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: method otherId(String)`.

- [ ] **Step 3: Implement**

`PersonRecord`: add the component after `zoobank`, the builder field and method, and pass it in `build()`:

```java
 * @param otherIds  further prefixed ids the source gives the person, e.g. a second IPNI id of one Wikidata item: IPNI's
 *                  duplicate records of one author
 * @param relations to other persons by a prefixed authority id of the same source, e.g. wd:Q42
 */
public record PersonRecord(
  Provenance source,
  @Nullable String wikidata,
  @Nullable String ipni,
  @Nullable String zoobank,
  Set<String> otherIds,
  @Nullable String family,
```

```java
    final Set<String> otherIds = new LinkedHashSet<>();
```

```java
    /** a second id of an authority for the same person, prefixed */
    public void otherId(String prefixedId) {
      otherIds.add(prefixedId);
    }
```

```java
    public PersonRecord build() {
      return new PersonRecord(source, wikidata, ipni, zoobank, Collections.unmodifiableSet(new LinkedHashSet<>(otherIds)),
        Names.ordered(family, label), Names.ordered(given, label),
        suffix != null ? suffix : Names.suffix(label), born, died, activeFrom, activeTo, Set.copyOf(groups),
        List.copyOf(names), List.copyOf(relations));
    }
```

(`otherIds` keeps insertion order: the merger joins in that order, and `Set.copyOf` iterates in an order that changes
between JVM runs.)

`WikidataPersonSource.addIds`, replacing the P586 and P2006 cases (import `life.catalogue.matching.person.Person` and
`java.util.Locale` if the file lacks them):

```java
        // an item with several IPNI or ZooBank ids keeps the first as its own, the others are the same person
        case P586 -> {
          if (pb.ipni == null) pb.ipni = v;
          else if (!pb.ipni.equals(v)) pb.otherId(Person.IPNI + v);
        }
        case P2006 -> {
          String z = v.toUpperCase(Locale.ROOT);
          if (pb.zoobank == null) pb.zoobank = z;
          else if (!pb.zoobank.equals(z)) pb.otherId(Person.ZOOBANK + z);
        }
```

`PersonMerger`:

1. In `attach`, after `link(d, r, index);` and before `return d;`, add `linkOtherIds(d, r, drafts, index);`.
2. Add:

```java
  /**
   * An authority may give a person a second id of another authority - Wikidata lists two IPNI ids for IPNI's duplicate
   * records of one author. It becomes a former id, and a person holding it is joined. One another item holds as its own
   * stays with that item and is reported.
   */
  private void linkOtherIds(Draft d, PersonRecord r, List<Draft> drafts, Map<String, Draft> index) {
    for (String id : r.otherIds()) {
      Draft o = index.get(id);
      if (o != null && o != d) {
        if (differs(o.wikidata, d.wikidata)) {
          report.ambiguous.add(describe(o) + " holds " + id + ", which a " + r.source().value() + " record gives "
            + describe(d) + " as a second id");
          continue;
        }
        addFormer(d, id);
        if (join(d, o, drafts, index, "the second id " + id) == null) {
          d.formerIds.remove(id);
          continue;
        }
      } else {
        addFormer(d, id);
      }
      index.put(id, d);
    }
  }

  private static void addFormer(Draft d, @Nullable String id) {
    if (id != null && !id.equals(d.id) && !d.formerIds.contains(id)) {
      d.formerIds.add(id);
    }
  }
```

3. In `join`, replace the three id lines and the two former id lines:

```java
    // a redirect or a second id of an authority joins without asking whether the ids agree: a dropped id becomes a
    // former id, so it still finds keep, and is reported unless an authority gave it as a second id of the person
    keep.wikidata = keepId(keep, other, "wikidata", Person.WIKIDATA, keep.wikidata, other.wikidata);
    keep.ipni = keepId(keep, other, "ipni", Person.IPNI, keep.ipni, other.ipni);
    keep.zoobank = keepId(keep, other, "zoobank", Person.ZOOBANK, keep.zoobank, other.zoobank);
    addFormer(keep, other.id);
    other.formerIds.forEach(id -> addFormer(keep, id));
```

```java
  private String keepId(Draft keep, Draft other, String field, String prefix, @Nullable String kept, @Nullable String dropped) {
    if (kept == null) return dropped;
    if (dropped != null && !kept.equals(dropped)) {
      if (!keep.formerIds.contains(prefix + dropped)) {
        report.conflicts.add(describe(keep) + " " + field + ": kept " + kept + ", dropped " + dropped + " of " + describe(other));
      }
      addFormer(keep, prefix + dropped);
    }
    return kept;
  }
```

4. In `linkId`, report a differing id only when it is no former id:

```java
    if (current != null) {
      if (!current.equals(value) && !d.formerIds.contains(prefix + value)) {
```

5. `docs/AUTHOR-PERSONS.md`, the `id` bullet: after "...keeps the old one in `formerIds`, so every line that refers to it
   keeps resolving." add: "A second id an authority gives the person is a former id too: Wikidata lists two IPNI ids for
   IPNI's duplicate records of one author, and the IPNI records of both make one person."

- [ ] **Step 4: Run the tests to see them pass**

Run: `mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass (59 before, 63 now).

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/harvest docs/AUTHOR-PERSONS.md
git commit -m "test(authorship): several ids of one authority make one person

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 2: Derived forms and family names

Three gaps the corpus needs closed. `Hooker f.` resolves to nobody, because the family name with its suffix is not
derived. `K.B. Presl` resolves to nobody: Wikidata calls him Karel only in a variant, and IPNI's forename
`Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)` makes seven initials. And Wikidata's several family names of
one person are alternatives (Carl Linnaeus has `Linné`, `von Linné`), yet they were joined into `Linné von Linné`.

**Files:**
- Modify: `core/src/main/java/life/catalogue/matching/person/PersonRegistry.java`
- Modify: `core/src/test/java/life/catalogue/matching/person/harvest/Names.java`,
  `core/src/test/java/life/catalogue/matching/person/harvest/PersonRecord.java` (`build()`)
- Modify: `docs/AUTHOR-PERSONS.md` (family bullet, "Looking up a citation")
- Test: `core/src/test/java/life/catalogue/matching/person/PersonRegistryTest.java`,
  `core/src/test/java/life/catalogue/matching/person/harvest/NamesTest.java`

**Interfaces:**
- Produces: `Names.family(List<String> parts, @Nullable String label)`; registry forms `family + suffix` and initials of
  `VARIANT`s ending with the family name.

- [ ] **Step 1: Write the failing tests**

In `PersonRegistryTest`:

```java
  /** relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II" */
  @Test
  public void familyWithSuffix() {
    var hooker = new Person("wd:Q157501", "Q157501", "4084-1", null, List.of(), "Hooker", "Joseph Dalton", "f.", 1817, 1911, null,
      null, Set.of(), Provenance.WIKIDATA);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(hooker, SOWERBY2),
      List.of(new PersonName("wd:Q157501", "Hook.f.", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q2", "G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA)),
      List.of()));
    assertEquals(Set.of(hooker), reg.candidates("Hooker f.", NomCode.BOTANICAL));
    assertEquals(Set.of(hooker), reg.candidates("Hooker fil.", NomCode.BOTANICAL));
    assertEquals(Set.of(SOWERBY2), reg.candidates("Sowerby II", NomCode.ZOOLOGICAL));
  }

  /** IPNI lists alternative forenames in brackets, and a variant ending with the family name gives initials too */
  @Test
  public void initialsOfVariantsWithoutBracketedAlternatives() {
    assertEquals("C. B. ", PersonRegistry.initials("Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)"));
    var presl = new Person("wd:Q5", "Q5", null, null, List.of(), "Presl", "Carl (Karl, Carel, Carolus) Bořivoj (Boriwog, Boriwag)",
      null, 1794, 1852, null, null, Set.of(), Provenance.IPNI);
    var reg = new PersonRegistry(new PersonFiles.Content(List.of(presl),
      List.of(new PersonName("wd:Q5", "C.Presl", NameKind.STANDARD, FormCode.BOT, Provenance.IPNI),
        new PersonName("wd:Q5", "Karel Bořivoj Presl", NameKind.VARIANT, FormCode.ANY, Provenance.WIKIDATA)),
      List.of()));
    assertEquals(Set.of(presl), reg.candidates("K.B. Presl", NomCode.BOTANICAL));
    assertEquals(Set.of(presl), reg.candidates("C. B. Presl", NomCode.BOTANICAL));
  }
```

In `NamesTest`:

```java
  /** several family names of one person are alternatives - maiden and married, latinised - unless the label holds them */
  @Test
  public void family() {
    assertEquals("Ruiz López", Names.family(List.of("López", "Ruiz"), "Hipólito Ruiz López"));
    assertEquals("Linnaeus", Names.family(List.of("Linné", "von Linné", "Linnaeus"), "Carl Linnaeus"));
    assertEquals("Linné", Names.family(List.of("Linné", "von Linné"), "Carl Linnaeus"));
    assertEquals("Married", Names.family(List.of("Maiden", "Married"), "Anna Married"));
    assertEquals("Smith", Names.family(List.of("Smith"), null));
    assertNull(Names.family(List.of(), "Carl Linnaeus"));
  }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `mvn -o -pl core test -Dtest='PersonRegistryTest,NamesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: method family(List<String>,String)`.

- [ ] **Step 3: Implement**

`Names`:

```java
  /**
   * Several family names of one person are alternatives - a maiden and a married name, a latinised one - unless the
   * label holds them together, as it does for "Ruiz López".
   *
   * @return the parts the label holds, in its order, else the first part; null for none
   */
  @Nullable
  static String family(List<String> parts, @Nullable String label) {
    if (parts.isEmpty()) return null;
    if (label != null) {
      List<String> held = parts.stream().filter(label::contains).toList();
      if (!held.isEmpty()) return ordered(held, label);
    }
    return parts.get(0);
  }
```

`PersonRecord.Builder.build()`: `Names.family(family, label)` in place of `Names.ordered(family, label)`.

`PersonRegistry`:

```java
      if (p.family() != null) {
        add(p.family(), p, FormCode.ANY);
        add(initials(p.given()) + p.family() + suffix(p), p, FormCode.ANY);
        if (p.suffix() != null) {
          // relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II"
          add(p.family() + suffix(p), p, FormCode.ANY);
        }
      }
```

```java
      if ((n.kind() == NameKind.FULL || n.kind() == NameKind.VARIANT) && p.family() != null) {
```

```java
    // IPNI lists alternative forenames in brackets: "Carl (Karl, Carel, Carolus) Bořivoj"
    for (String part : given.replaceAll("\\([^)]*\\)", " ").split("[\\s-]+")) {
```

Update the class comment: forms are derived from "the initials of the given names with family name and suffix, the same
of every full name or variant that ends with the family name and suffix, the family name with its suffix ("Hooker f.")
and the bare family name".

`docs/AUTHOR-PERSONS.md`:
- In the `family` bullet, add: "Wikidata's several family names of one person are alternatives - a maiden and a married
  name, a latinised one - so only those its label holds are kept, else the first."
- In "Looking up a citation", say that forms are derived from `FULL` and `VARIANT` forms ending with the family name, that
  the family name with its suffix is a form (`hooker filius`), and that bracketed alternatives of IPNI forenames give no
  initials.

- [ ] **Step 4: Run the tests to see them pass**

Run: `mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, `PersonRegistryFilesTest` included: the committed files are not regenerated yet and new forms add no
problem.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/life/catalogue/matching/person/PersonRegistry.java core/src/test/java/life/catalogue/matching/person docs/AUTHOR-PERSONS.md
git commit -m "feat(authorship): family and suffix forms, initials of variants, no joined family names

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 3: The regenerated registry with G. B. Sowerby III

Tasks 1 and 2 change what the harvest writes, and the merge never overwrites a value of the files, so the registry is
harvested again from the cache into files that hold only the curated lines. There is one curated person so far:
George Brettingham Sowerby III (Q1216378), whose Wikidata item has none of P428, P835, P586 or P2006, with his parent
G. B. Sowerby II (Q1223045). Phase 3's fixture needs him.

**Files:**
- Modify: `core/src/main/resources/authorship/persons/{persons,names,relations}.tsv`
- Test: `core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java`
- Modify: `docs/2026-09-23-person-author-comparison.md` (Outcome, phase 2 figures)

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: the registry Tasks 6 to 10 resolve against.

- [ ] **Step 1: Write the failing test**

In `PersonRegistryFilesTest` (imports `org.gbif.nameparser.api.NomCode`, `java.util.Set`,
`java.util.stream.Collectors`):

```java
  /** the relatives phase 3 is measured on resolve, each to one person, and G. B. Sowerby III knows his father */
  @Test
  public void committedRegistryKnowsTheRelatives() {
    var reg = PersonRegistry.get();
    Person sowerby3 = one(reg.candidates("G.B. Sowerby III", NomCode.ZOOLOGICAL));
    assertEquals("wd:Q1216378", sowerby3.id());
    assertEquals(Set.of("wd:Q1223045"), reg.relatives(sowerby3).stream().map(Person::id).collect(Collectors.toSet()));
    assertEquals("wd:Q157501", one(reg.candidates("Hooker f.", NomCode.BOTANICAL)).id());
    assertEquals("wd:Q379601", one(reg.candidates("K.B. Presl", NomCode.BOTANICAL)).id());
    assertEquals("wd:Q379601", one(reg.candidates("C.Presl", NomCode.BOTANICAL)).id());
    assertEquals("Linné", reg.get("wd:Q1043").family());
  }

  private static Person one(Set<Person> persons) {
    assertEquals(persons.toString(), 1, persons.size());
    return persons.iterator().next();
  }
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn -o -pl core test -Dtest=PersonRegistryFilesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL on `G.B. Sowerby III`, `[]` of size 0.

- [ ] **Step 3: Seed the curated lines and harvest from the cache**

```bash
cd core
S=target/person-seed; mkdir -p $S
for f in persons names relations; do head -1 src/main/resources/authorship/persons/$f.tsv > $S/$f.tsv; done
printf 'wd:Q1216378\tQ1216378\t\t\t\tSowerby\tGeorge Brettingham\tIII\t1843\t1921\t\t\tMolluscs\tcurated\n' >> $S/persons.tsv
printf 'wd:Q1216378\tGeorge Brettingham Sowerby III\tFULL\tANY\tcurated\nwd:Q1216378\tG.B. Sowerby III\tCITATION\tZOO\tcurated\n' >> $S/names.tsv
printf 'wd:Q1216378\tPARENT\twd:Q1223045\tcurated\n' >> $S/relations.tsv
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.person.harvest.PersonHarvest target/person-seed target/person-harvest"
```

Expected: `Full report in target/person-harvest/report.txt` within minutes. Only the redirect query for Q1216378 is a
new request; everything else comes from the cache. The report's first line shows persons before 1 and about 87,100 added,
fewer than the 87,300 of phase 2 by roughly the 160 second IPNI ids now joined.

- [ ] **Step 4: Check the report and install the files**

```bash
grep -E '^## |^persons|^dropped|^ipni:' target/person-harvest/report.txt
cp target/person-seed/*.tsv src/main/resources/authorship/persons/
grep -c 'curated$' src/main/resources/authorship/persons/*.tsv
```

Expected: 1, 2 and 1 curated lines. "Authority ids claimed by two persons" at most the 28 of phase 2.

- [ ] **Step 5: Run the person tests**

Run: `mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, `committedRegistryKnowsTheRelatives` included.

- [ ] **Step 6: Update the Outcome and commit**

In the design record's phase 2 paragraph, replace the figures with the new ones (persons, name forms, relations,
unresolved author map rows) and add one sentence: the registry was harvested again after phase 3's registry fixes,
with the one curated person, G. B. Sowerby III.

```bash
cd ..
git add core/src/main/resources/authorship/persons core/src/test/java/life/catalogue/matching/person/PersonRegistryFilesTest.java docs/2026-09-23-person-author-comparison.md
git commit -m "feat(authorship): the registry harvested again, with G. B. Sowerby III curated

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 4: Corpus pairs carry their taxonomic group

The person matcher narrows by the group of the names. A pair takes it from its two example names (`ExportRow.group()`)
as the group both belong to: the broader of two nested groups, none for disparate ones. It goes in a last `group`
column, so pairs files written before it still read.

**Files:**
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorPair.java`, `AuthorPairMiner.java:370`,
  `CorpusIO.java:130-132`, `CorpusEvaluator.java:79-84`, `AuthorPairSamplerTest.java:23`, `CorpusEvaluatorTest.java:22-29`
- Modify: `dao/src/test/resources/author-corpus/fixture-export.tsv` (a `classification` column)
- Create: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorPairTest.java`
- Modify: `docs/AUTHOR-CORPUS.md` (the pairs row of the file table)
- Test: `CorpusIOTest`, `AuthorPairMinerTest`, `CorpusEvaluatorTest`, `AuthorPairTest`

**Interfaces:**
- Produces: `AuthorPair.group()` (`@Nullable TaxGroup`, the last record component), `AuthorPair.commonGroup(TaxGroup,
  TaxGroup)`, `AuthorPair.COLUMNS` ending with `group`, `AuthorPair.COLUMNS_WITHOUT_GROUP`. `CorpusEvaluator` passes the
  group to `AuthorComparator.compare(ScientificName, ScientificName, TaxGroup)`.

- [ ] **Step 1: Write the failing tests**

`AuthorPairTest`:

```java
package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AuthorPairTest {

  /** the group both names of a pair belong to: the broader of two nested ones, none for disparate ones */
  @Test
  public void commonGroup() {
    assertEquals(TaxGroup.Plants, AuthorPair.commonGroup(TaxGroup.Plants, TaxGroup.Angiosperms));
    assertEquals(TaxGroup.Plants, AuthorPair.commonGroup(TaxGroup.Angiosperms, TaxGroup.Plants));
    assertEquals(TaxGroup.Molluscs, AuthorPair.commonGroup(null, TaxGroup.Molluscs));
    assertNull(AuthorPair.commonGroup(TaxGroup.Molluscs, TaxGroup.Angiosperms));
    assertNull(AuthorPair.commonGroup(null, null));
  }
}
```

In `CorpusIOTest`:

```java
  /** a pairs file from before the group column still reads, the committed fixtures among them */
  @Test
  public void pairsWithoutGroup() throws Exception {
    var pairs = CorpusIO.readPairs(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS));
    assertEquals(9, pairs.size());
    assertTrue(pairs.stream().allMatch(p -> p.group() == null));
  }

  @Test
  public void pairGroupRoundTrip() throws Exception {
    AuthorPair p = CorpusIO.readPairs(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS)).get(0);
    AuthorPair g = new AuthorPair(p.label(), p.source(), p.weight(), p.stat(), p.code(), p.rank(), p.nidx(), p.scientificName(),
      p.keyA(), p.keyB(), p.a(), p.b(), TaxGroup.Gastropods);
    File f = tmp.newFile("pairs.tsv");
    try (var w = CorpusIO.writer(f, AuthorPair.COLUMNS)) {
      w.write(g.toRow());
    }
    assertEquals(List.of(g), CorpusIO.readPairs(f));
  }
```

(`CorpusIOTest` needs `import life.catalogue.common.io.Resources;` if it lacks it.)

In `AuthorPairMinerTest`:

```java
  /** a pair carries the group of its names, derived from their classification */
  @Test
  public void pairsCarryTheirGroup() {
    assertEquals(TaxGroup.Angiosperms, pair(";;;dc", ";;;decandolle").group());
  }
```

In `CorpusEvaluatorTest`, give `pair(...)` a group parameter: rename the existing helper's body into
`pair(Label, NomCode, int, String, String, String, String, @Nullable TaxGroup)` passing `group` as the last constructor
argument, and keep the old signature delegating with `null`. Then add:

```java
  /** the group of the pair reaches the matcher, with the years and without */
  @Test
  public void passesTheGroup() {
    List<TaxGroup> groups = new ArrayList<>();
    var e = new CorpusEvaluator(new AuthorComparator((t1, t2, ctx, mode) -> {
      groups.add(ctx.group());
      return Equality.EQUAL;
    }));
    e.evaluate(pair(Label.SAME, NomCode.ZOOLOGICAL, 1, "Sowerby", "1842", "G. B. Sowerby II", "1842", TaxGroup.Gastropods));
    assertEquals(List.of(TaxGroup.Gastropods, TaxGroup.Gastropods), groups);
  }
```

Give `fixture-export.tsv` its classification: the three de Candolle groups (`Aus ius`, `Aus jus`, `Aus kus`) get
`Plantae|Magnoliopsida`, the others an empty cell:

```bash
cd dao/src/test/resources/author-corpus
awk -F'\t' -v OFS='\t' 'NR==1 {print $0, "classification"; next} $7 ~ /^Aus [ijk]us$/ {print $0, "Plantae|Magnoliopsida"; next} {print $0, ""}' \
  fixture-export.tsv > fixture-export.tmp && mv fixture-export.tmp fixture-export.tsv
cd -
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `mvn -o -pl dao test -Dtest='AuthorPairTest,CorpusIOTest,AuthorPairMinerTest,CorpusEvaluatorTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: method commonGroup` and `method group()`.

- [ ] **Step 3: Implement**

`AuthorPair`:

```java
 * @param group  the taxonomic group of the two names, see {@link #commonGroup}; null if unknown or disparate
 */
public record AuthorPair(
  Label label, Source source, int weight, PairStat stat,
  @Nullable NomCode code, String rank, int nidx, String scientificName,
  String keyA, String keyB, Side a, Side b, @Nullable TaxGroup group
) {
```

```java
  public static final List<String> COLUMNS = Stream.of(
    Stream.of("label", "source", "weight", "support", "yearAgree", "yearConflict", "freqA", "freqB",
      "intraYearDiff", "intraNoYear", "intraYearAgree", "code", "rank", "nidx", "scientificName", "keyA", "keyB"),
    SIDE_COLUMNS.stream().map(c -> c + "A"),
    SIDE_COLUMNS.stream().map(c -> c + "B"),
    Stream.of("group")
  ).flatMap(s -> s).toList();
  /** the columns of a pairs file from before the group, which still reads */
  public static final List<String> COLUMNS_WITHOUT_GROUP = COLUMNS.subList(0, COLUMNS.size() - 1);

  /**
   * @return the group both names belong to: the broader of two nested groups, the one known if the other is not, null
   *         for disparate ones
   */
  @Nullable
  public static TaxGroup commonGroup(@Nullable TaxGroup a, @Nullable TaxGroup b) {
    if (a == null) return b;
    if (b == null) return a;
    if (a.contains(b)) return a;
    if (b.contains(a)) return b;
    return null;
  }
```

In `toRow()` after `b.addTo(row);`: `row.add(group == null ? null : group.name());`. In `of(String[] row)` pass
`Side.of(row, 17 + SIDE_COLUMNS.size()), group(ExportRow.col(row, COLUMNS.size() - 1))` with:

```java
  @Nullable
  private static TaxGroup group(@Nullable String name) {
    return name == null ? null : TaxGroup.valueOf(name);
  }
```

`AuthorPairMiner.write`: `..., AuthorPair.Side.of(a), AuthorPair.Side.of(b), AuthorPair.commonGroup(a.group(), b.group()));`

`CorpusIO.readPairs(File, Consumer)`:

```java
  public static void readPairs(File pairs, Consumer<AuthorPair> pairConsumer) throws IOException {
    try (TabReader reader = TabReader.tab(open(pairs), StandardCharsets.UTF_8, 0, 1)) {
      var iter = reader.iterator();
      String[] header = iter.hasNext() ? iter.next() : new String[0];
      if (!AuthorPair.COLUMNS_WITHOUT_GROUP.equals(Arrays.asList(header))) {
        verifyHeader(pairs, AuthorPair.COLUMNS, header);
      }
      while (iter.hasNext()) {
        pairConsumer.accept(AuthorPair.of(iter.next()));
      }
    }
  }
```

`CorpusEvaluator.evaluate(AuthorPair)`:

```java
    return new Verdict(p,
      comparator.compare(p.a().toName(p.code(), true), p.b().toName(p.code(), true), p.group()),
      comparator.compare(p.a().toName(p.code(), false), p.b().toName(p.code(), false), p.group())
    );
```

`AuthorPairSamplerTest` helper: pass `null` as the new last argument.

`docs/AUTHOR-CORPUS.md`, the pairs row of the file table: append ``, then `group`: the taxonomic group both names belong
to, derived from their classification (`AuthorPair.commonGroup`), empty when unknown or disparate``.

- [ ] **Step 4: Run the tests to see them pass**

Run: `mvn -o -pl dao test -Dtest='life.catalogue.matching.authorship.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Re-mine the classified corpus and rerun the string baseline**

```bash
mvn -o -q -pl dao -am install -DskipTests
cd dao
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairMiner target/author-corpus/classified/author-corpus.tsv.gz target/author-corpus/classified/pairs.tsv.gz"
cp target/author-corpus/classified/verdicts.tsv.gz target/author-corpus/classified/verdicts-before-group.tsv.gz
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport target/author-corpus/classified/pairs.tsv.gz target/author-corpus/classified"
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-cp %classpath life.catalogue.matching.authorship.corpus.AuthorVerdictDiff target/author-corpus/classified/verdicts-before-group.tsv.gz target/author-corpus/classified/verdicts.tsv.gz"
gzip -dc target/author-corpus/classified/pairs.tsv.gz | awk -F'\t' 'NR>1 {g[$38==""?"none":$38]++} END {for (k in g) print g[k], k}' | sort -rn | head -12
cd ..
```

Expected: labels unchanged (`SAME=50044, DIFF=95532, DUBIOUS=274292`); the diff shows 0 flips, as the string matcher
ignores the group; the group count lists Angiosperms first.

- [ ] **Step 6: Commit**

```bash
git add dao/src/test docs/AUTHOR-CORPUS.md
git commit -m "test(authorship): corpus pairs carry the taxonomic group of their names

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 5: The corpus report takes the comparator it measures

The spec: `AuthorCorpusReport` takes the comparator to measure instead of building one. An `Extension` sees every pair
before and after it is judged and appends its own sections, which is how the person report tells which rule decided.

**Files:**
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReport.java`
- Test: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReportTest.java`

**Interfaces:**
- Produces: `AuthorCorpusReport.Extension` (`before(AuthorPair)`, `after(CorpusEvaluator.Verdict)`,
  `render(StringBuilder)`, all default no-ops); `AuthorCorpusReport.report(File pairs, File outDir, AuthorComparator
  comparator, List<String> header, @Nullable AuthorshipNormalizer aliasCheck, Extension extension)`; public
  `AuthorCorpusReport.REPORT` and `AuthorCorpusReport.VERDICTS`.

- [ ] **Step 1: Write the failing test**

In `AuthorCorpusReportTest` (imports `life.catalogue.common.tax.AuthorshipNormalizer`,
`life.catalogue.matching.authorship.AuthorComparator`, `java.util.ArrayList`):

```java
  /** a report on another comparator: its own header lines and sections, every pair shown to its extension, no alias list */
  @Test
  public void reportOnAnotherComparator() throws Exception {
    File dir = tmp.newFolder("other");
    List<String> seen = new ArrayList<>();
    var ext = new AuthorCorpusReport.Extension() {
      @Override
      public void before(AuthorPair p) {
        seen.add("before " + p.keyA());
      }

      @Override
      public void after(CorpusEvaluator.Verdict v) {
        seen.add("after " + v.pair().keyA());
      }

      @Override
      public void render(StringBuilder sb) {
        sb.append("\n## Extension\nrendered\n");
      }
    };
    AuthorCorpusReport.report(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS), dir,
      new AuthorComparator(AuthorshipNormalizer.INSTANCE), List.of("comparator: test"), null, ext);
    String r = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    assertTrue(r, r.lines().limit(8).anyMatch(l -> l.equals("comparator: test")));
    assertFalse(r, r.contains("Alias candidates"));
    assertTrue(r, r.contains("## Extension\nrendered"));
    assertEquals(18, seen.size());
    assertEquals("before ;;;martin", seen.get(0));
    assertEquals("after ;;;martin", seen.get(1));
  }
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn -o -pl dao test -Dtest=AuthorCorpusReportTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: class Extension`.

- [ ] **Step 3: Implement**

Make `REPORT` and `VERDICTS` public. Add the extension and split `report`:

```java
  /**
   * What a report on a particular comparator adds. It sees every pair before and after it is judged and appends its own
   * sections: the person matcher tells from it which rule decided a verdict.
   */
  public interface Extension {
    default void before(AuthorPair pair) {
    }

    default void after(Verdict verdict) {
    }

    default void render(StringBuilder sb) {
    }
  }

  private static final Extension NONE = new Extension() {
  };

  /**
   * @param withAuthorMap false to compare without the author map. Diffed against a report with it, that shows
   *                      which pairs the map gets right and which it breaks
   */
  public static void report(File pairs, File outDir, boolean withAuthorMap) throws IOException {
    AuthorshipNormalizer normalizer = withAuthorMap ? AuthorshipNormalizer.INSTANCE : AuthorshipNormalizer.createWithoutAuthormap();
    // says which author map the verdicts come from: -pl dao takes it from the api jar in ~/.m2, not from the checkout
    String map = withAuthorMap ? String.format("author map: %,d rows", Resources.lines(AUTHOR_MAP).count()) : "author map: none";
    report(pairs, outDir, new AuthorComparator(normalizer), List.of(map), withAuthorMap ? normalizer : null, NONE);
  }

  /**
   * @param header     what was measured, one line each, printed below the input
   * @param aliasCheck the normalizer whose author map the alias worklist checks, null to leave that list out
   */
  public static void report(File pairs, File outDir, AuthorComparator comparator, List<String> header,
                            @Nullable AuthorshipNormalizer aliasCheck, Extension extension) throws IOException {
```

In the body of the second method:
- `final CorpusEvaluator evaluator = new CorpusEvaluator(comparator);` replaces the normalizer and evaluator lines.
- The alias `Top` is only in the list with an `aliasCheck`: build the list as
  `Stream.of(...).filter(Objects::nonNull).toList()`, with that element written as
  `aliasCheck == null ? null : new Top("Alias candidates the author map lacks", ..., v -> v.pair().label() == Label.SAME && v.pair().weight() >= MIN_ALIAS_WEIGHT && lacksAlias(aliasCheck, v.pair()))`.
- In the pair loop: `extension.before(p);` before `Verdict v = evaluator.evaluate(p);` and `extension.after(v);` right after.
- `header.forEach(l -> sb.append(l).append('\n'));` replaces the author map line of the header.
- `extension.render(sb);` after `tops.forEach(t -> t.render(sb));`.

Imports: `javax.annotation.Nullable`, `java.util.stream.Stream`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `mvn -o -pl dao test -Dtest='life.catalogue.matching.authorship.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, the existing report tests unchanged.

- [ ] **Step 5: Install and commit**

```bash
mvn -o -q -pl dao -am install -DskipTests
git add dao/src/test
git commit -m "test(authorship): the corpus report takes the comparator it measures

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 6: PersonResolver

Resolves a citation to the persons it may name and narrows them by the name's year and group (spec 4.1 and 4.2).

**Files:**
- Create: `core/src/main/java/life/catalogue/matching/person/PersonResolver.java`
- Test: `core/src/test/java/life/catalogue/matching/person/PersonResolverTest.java`

**Interfaces:**
- Consumes: `PersonRegistry.candidates(String, NomCode)`.
- Produces: `PersonResolver(PersonRegistry, Margins)`, `Set<Person> resolve(String citation, @Nullable NomCode code,
  @Nullable Integer year, @Nullable TaxGroup group)`, `static @Nullable Integer year(@Nullable String)`,
  `record Margins(int minAge, int posthumous, int activeSlack)` with `Margins.DEFAULT = new Margins(10, 20, 15)`.

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonResolverTest {
  static final NomCode ZOO = NomCode.ZOOLOGICAL;

  static Person person(String q, String given, String suffix, Integer born, Integer died, Integer activeFrom, Integer activeTo,
                       Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), "Sowerby", given, suffix, born, died, activeFrom, activeTo, groups,
      Provenance.WIKIDATA);
  }

  static final Person JAMES1 = person("Q1", "James", null, 1757, 1822, null, null, Set.of());
  static final Person JAMES2 = person("Q2", "James", null, 1815, 1834, null, null, Set.of());
  static final Person GBS2 = person("Q3", "George Brettingham", "II", 1812, 1884, null, null, Set.of(TaxGroup.Molluscs));
  static final Person FLORA = person("Q4", "Flora", null, null, null, 1880, 1890, Set.of());
  static final Person BOTANIST = person("Q5", "Bartholomew", null, null, null, null, null, Set.of(TaxGroup.Angiosperms));

  // explicit margins: the tests must not move when the defaults are set on the corpus
  private final PersonResolver resolver = new PersonResolver(new PersonRegistry(new PersonFiles.Content(
    List.of(JAMES1, JAMES2, GBS2, FLORA, BOTANIST),
    List.of(new PersonName("wd:Q1", "James Sowerby", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
      new PersonName("wd:Q2", "James Sowerby", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
      new PersonName("wd:Q3", "G.B. Sowerby II", NameKind.CITATION, FormCode.ZOO, Provenance.WIKIDATA),
      new PersonName("wd:Q4", "Flora Sowerby", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA),
      new PersonName("wd:Q5", "Bartholomew Sowerby", NameKind.FULL, FormCode.ANY, Provenance.WIKIDATA)),
    List.of())), new PersonResolver.Margins(10, 20, 15));

  /** "J. Sowerby" of 1821 is the elder James, the younger was six; after 1822 + 20 only the younger is left */
  @Test
  public void yearOfTheName() {
    assertEquals(Set.of(JAMES1), resolver.resolve("J. Sowerby", ZOO, 1821, null));
    assertEquals(Set.of(JAMES2), resolver.resolve("J. Sowerby", ZOO, 1850, null));
    assertEquals(Set.of(), resolver.resolve("J. Sowerby", ZOO, 1860, null));
    assertEquals(Set.of(JAMES1, JAMES2), resolver.resolve("J. Sowerby", ZOO, null, null));
  }

  /** Flora is only known active 1880 to 1890: 15 years either way */
  @Test
  public void activeYearsWithoutLifeDates() {
    assertTrue(resolver.resolve("Sowerby", ZOO, 1870, null).contains(FLORA));
    assertFalse(resolver.resolve("Sowerby", ZOO, 1860, null).contains(FLORA));
    assertFalse(resolver.resolve("Sowerby", ZOO, 1910, null).contains(FLORA));
  }

  /** a botanist is no author of a snail; a person without groups may be anyone's; a broader group is no contradiction */
  @Test
  public void groupOfTheName() {
    Set<Person> snail = resolver.resolve("Sowerby", null, null, TaxGroup.Gastropods);
    assertFalse(snail.contains(BOTANIST));
    assertTrue(snail.contains(JAMES1));
    assertTrue(snail.contains(GBS2));
    assertTrue(resolver.resolve("Sowerby", null, null, TaxGroup.Plants).contains(BOTANIST));
  }

  /** the comparator hands over authors normalized already: they resolve like the citation they came from */
  @Test
  public void normalizedCitationsResolveAlike() {
    for (String c : List.of("G.B. Sowerby II", "J. Sowerby", "Sowerby", "G. B. Sowerby II")) {
      assertEquals(c, resolver.resolve(c, ZOO, null, null), resolver.resolve(AuthorshipNormalizer.normalize(c), ZOO, null, null));
    }
    assertEquals(Set.of(GBS2), resolver.resolve("G.B. Sowerby II", ZOO, null, null));
  }

  /** the year as the parser gives it: an imprecise one narrows nothing */
  @Test
  public void years() {
    assertEquals(Integer.valueOf(1753), PersonResolver.year("1753"));
    assertEquals(Integer.valueOf(1878), PersonResolver.year("1878 [1879]"));
    assertNull(PersonResolver.year("184?"));
    assertNull(PersonResolver.year("12345"));
    assertNull(PersonResolver.year(null));
    assertEquals(Set.of(JAMES1, JAMES2), resolver.resolve("J. Sowerby", ZOO, PersonResolver.year("184?"), null));
  }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn -o -pl core test -Dtest=PersonResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: class PersonResolver`.

- [ ] **Step 3: Implement**

```java
package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Resolves an author citation to the persons of the registry it may name under the code of the name, and narrows them by
 * what is known about the name: its year and its taxonomic group. A person without years or groups is never ruled out.
 * The candidates of a citation are cached per code, the narrowing runs on every call.
 */
public class PersonResolver {
  private static final Pattern YEAR = Pattern.compile("(?<!\\d)(1[5-9]\\d\\d|20\\d\\d)(?!\\d)");

  /**
   * @param minAge      nobody publishes a name before this age
   * @param posthumous  years after a death a name may still appear under the dead
   * @param activeSlack years around the active years of a person without life dates
   */
  public record Margins(int minAge, int posthumous, int activeSlack) {
    public static final Margins DEFAULT = new Margins(10, 20, 15);
  }

  private final PersonRegistry registry;
  private final Margins margins;
  private final Map<String, Set<Person>> candidates = new ConcurrentHashMap<>();

  public PersonResolver(PersonRegistry registry, Margins margins) {
    this.registry = registry;
    this.margins = margins;
  }

  /**
   * @param citation an author as cited, or as {@link life.catalogue.common.tax.AuthorshipNormalizer} normalized it, which
   *                 folds to the same key
   * @return the persons the citation may name, empty for none or if every one was ruled out
   */
  public Set<Person> resolve(String citation, @Nullable NomCode code, @Nullable Integer year, @Nullable TaxGroup group) {
    Set<Person> all = candidates.computeIfAbsent((code == null ? "" : code.name()) + '|' + citation,
      k -> Collections.unmodifiableSet(registry.candidates(citation, code)));
    if (all.isEmpty() || (year == null && group == null)) {
      return all;
    }
    return all.stream().filter(p -> possible(p, year, group)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean possible(Person p, @Nullable Integer year, @Nullable TaxGroup group) {
    if (year != null) {
      if (p.born() != null && year < p.born() + margins.minAge()) return false;
      if (p.died() != null && year > p.died() + margins.posthumous()) return false;
      if (p.born() == null && p.activeFrom() != null && year < p.activeFrom() - margins.activeSlack()) return false;
      if (p.died() == null && p.activeTo() != null && year > p.activeTo() + margins.activeSlack()) return false;
    }
    return group == null || p.groups().isEmpty() || !p.groups().stream().allMatch(g -> g.isDisparateTo(group));
  }

  /**
   * @param year as the parser gives it: "1753", "1878 [1879]", "184?"
   * @return the first full year, null for none or an imprecise one
   */
  @Nullable
  public static Integer year(@Nullable String year) {
    if (year == null) return null;
    Matcher m = YEAR.matcher(year);
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `mvn -o -pl core test -Dtest=PersonResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/life/catalogue/matching/person/PersonResolver.java core/src/test/java/life/catalogue/matching/person/PersonResolverTest.java
git commit -m "feat(authorship): resolve citations to persons, narrowed by the year and group of the name

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 7: PersonAuthorMatcher

Decides author pairs from the resolved persons (spec 4.3 to 4.5) and explains every decision. Its fallback needs a
person's forms, which the registry exposes as normalized keys.

**Files:**
- Create: `core/src/main/java/life/catalogue/matching/person/PersonAuthorMatcher.java`
- Modify: `core/src/main/java/life/catalogue/matching/person/PersonRegistry.java` (`keys(Person, NomCode)`)
- Test: `core/src/test/java/life/catalogue/matching/person/PersonAuthorMatcherTest.java`,
  `core/src/test/java/life/catalogue/matching/person/PersonRegistryTest.java`

**Interfaces:**
- Consumes: `PersonResolver.resolve`, `PersonResolver.year`, `AuthorMatcher`, `AuthorTeam`, `AuthorContext`.
- Produces: `PersonAuthorMatcher(PersonRegistry, PersonResolver, AuthorMatcher fallback, RelativesPolicy, @Nullable
  Consumer<Explanation> listener)`, `Explanation explain(AuthorTeam, AuthorTeam, AuthorContext, Mode)`, enums
  `RelativesPolicy {UNKNOWN, DIFFERENT}` and `Rule {IDENTICAL, IDENTITY, RELATIVES, FALLBACK}`, records
  `Decision(String author1, String author2, Set<Person> persons1, Set<Person> persons2, Rule rule, Equality verdict)` and
  `Explanation(AuthorTeam team1, AuthorTeam team2, AuthorContext context, Equality verdict, List<Decision> decisions)`;
  `PersonRegistry.keys(Person, @Nullable NomCode)` returning `Set<String>`.

The spec's `explain(t1, t2, ctx)` takes the mode as well: the fallback runs in the mode it is called with.

- [ ] **Step 1: Write the failing tests**

In `PersonRegistryTest`:

```java
  /** the keys of a person's forms, derived ones included, as the fallback compares them */
  @Test
  public void keysOfAPerson() {
    var reg = registry();
    assertEquals(Set.of("sw", "olof swartz", "o swartz", "swartz"), reg.keys(SWARTZ, NomCode.BOTANICAL));
    assertEquals(Set.of("olof swartz", "o swartz", "swartz"), reg.keys(SWARTZ, NomCode.ZOOLOGICAL));
  }
```

`PersonAuthorMatcherTest` (ids are made up, the persons are real enough to read):

```java
package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.*;
import life.catalogue.matching.authorship.AuthorMatcher.Mode;
import life.catalogue.matching.person.PersonAuthorMatcher.Explanation;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;
import life.catalogue.matching.person.PersonAuthorMatcher.Rule;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersonAuthorMatcherTest {

  static Person person(String q, String family, String given, String suffix, Integer born, Integer died, Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), family, given, suffix, born, died, null, null, groups, Provenance.WIKIDATA);
  }

  static final Person WJ_HOOKER = person("Q11", "Hooker", "William Jackson", null, 1785, 1865, Set.of(TaxGroup.Plants));
  static final Person JD_HOOKER = person("Q12", "Hooker", "Joseph Dalton", "f.", 1817, 1911, Set.of(TaxGroup.Plants));
  static final Person C_HOOK = person("Q13", "Hook", "Cathy J.", null, null, null, Set.of(TaxGroup.Molluscs));
  static final Person MILLER = person("Q14", "Miller", "Philip", null, 1691, 1771, Set.of());
  static final Person LINNAEUS = person("Q15", "Linnaeus", "Carl", null, 1707, 1778, Set.of());
  static final Person ARNOTT = person("Q16", "Arnott", "George Arnott Walker", null, 1799, 1868, Set.of());
  static final Person COX = person("Q17", "Cox", "Leslie Reginald", null, 1897, 1965, Set.of());
  static final Person SOWERBY1 = person("Q18", "Sowerby", "George Brettingham", "I", 1788, 1854, Set.of(TaxGroup.Molluscs));
  static final Person SOWERBY2 = person("Q19", "Sowerby", "George Brettingham", "II", 1812, 1884, Set.of(TaxGroup.Molluscs));

  static PersonName name(Person p, String form, NameKind kind, FormCode code) {
    return new PersonName(p.id(), form, kind, code, Provenance.WIKIDATA);
  }

  private final PersonRegistry registry = new PersonRegistry(new PersonFiles.Content(
    List.of(WJ_HOOKER, JD_HOOKER, C_HOOK, MILLER, LINNAEUS, ARNOTT, COX, SOWERBY1, SOWERBY2),
    List.of(name(WJ_HOOKER, "Hook.", NameKind.STANDARD, FormCode.BOT), name(WJ_HOOKER, "William Jackson Hooker", NameKind.FULL, FormCode.ANY),
      name(JD_HOOKER, "Hook.f.", NameKind.STANDARD, FormCode.BOT), name(JD_HOOKER, "Joseph Dalton Hooker", NameKind.FULL, FormCode.ANY),
      name(C_HOOK, "Cathy J. Hook", NameKind.FULL, FormCode.ANY),
      name(MILLER, "Mill.", NameKind.STANDARD, FormCode.BOT), name(LINNAEUS, "L.", NameKind.STANDARD, FormCode.BOT),
      name(ARNOTT, "Arn.", NameKind.STANDARD, FormCode.BOT), name(COX, "Leslie Reginald Cox", NameKind.FULL, FormCode.ANY),
      name(SOWERBY1, "G.B. Sowerby I", NameKind.CITATION, FormCode.ZOO), name(SOWERBY2, "G.B. Sowerby II", NameKind.CITATION, FormCode.ZOO)),
    List.of(new PersonRelation(JD_HOOKER.id(), RelationType.PARENT, WJ_HOOKER.id(), Provenance.WIKIDATA),
      new PersonRelation(SOWERBY2.id(), RelationType.PARENT, SOWERBY1.id(), Provenance.WIKIDATA))));
  private final StringAuthorMatcher strings = new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE);
  private final List<Explanation> seen = new ArrayList<>();

  private PersonAuthorMatcher matcher(RelativesPolicy policy) {
    return new PersonAuthorMatcher(registry, new PersonResolver(registry, new PersonResolver.Margins(10, 20, 15)), strings, policy,
      seen::add);
  }

  /** a team as the comparator hands it over: normalized */
  static AuthorTeam team(@Nullable String year, String... authors) {
    return new AuthorTeam(Arrays.stream(authors).map(AuthorshipNormalizer::normalize).toList(), year);
  }

  static final AuthorContext BOT = new AuthorContext(NomCode.BOTANICAL, null);
  static final AuthorContext ZOO = new AuthorContext(NomCode.ZOOLOGICAL, null);

  @Test
  public void sharedPersonIsEqual() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1842", "Sowerby"), team("1842", "G.B. Sowerby II"), ZOO, Mode.LAX);
    assertEquals(Equality.EQUAL, e.verdict());
    assertEquals(Rule.IDENTITY, e.decisions().get(0).rule());
  }

  @Test
  public void unrelatedPersonsAreDifferent() {
    assertEquals(Equality.DIFFERENT, matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Mill."), team(null, "L."), BOT, Mode.LAX));
  }

  @Test
  public void relativesFollowThePolicy() {
    assertEquals(Equality.UNKNOWN, matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX));
    assertEquals(Equality.DIFFERENT, matcher(RelativesPolicy.DIFFERENT).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX));
    assertEquals(Rule.RELATIVES, seen.get(0).decisions().get(0).rule());
  }

  /** an unresolved citation meets every form of the persons on the other side: "L. Cox" meets the derived "L. R. Cox" */
  @Test
  public void unresolvedMeetsTheFormsOfTheOtherSide() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team(null, "L. Cox"), team(null, "Cox"), BOT, Mode.LAX);
    assertEquals(Equality.EQUAL, e.verdict());
    assertEquals(Rule.FALLBACK, e.decisions().get(0).rule());
    assertEquals(Set.of(), e.decisions().get(0).persons1());
    assertEquals(Set.of(COX), e.decisions().get(0).persons2());
  }

  /** with nobody known on either side the string comparison decides, as it would alone */
  @Test
  public void bothUnknownCompareAsStrings() {
    for (String[] p : List.of(new String[]{"Smith", "Smyth"}, new String[]{"Bory", "Bory de St.-Vincent"}, new String[]{"Rolfe", "Rolfe"})) {
      AuthorTeam t1 = team(null, p[0]);
      AuthorTeam t2 = team(null, p[1]);
      assertEquals(p[0], strings.compareTeams(t1, t2, BOT, Mode.LAX), matcher(RelativesPolicy.UNKNOWN).compareTeams(t1, t2, BOT, Mode.LAX));
    }
  }

  /** the year and the group of the name narrow what a citation may name */
  @Test
  public void narrowsByYearAndGroup() {
    var plants = new AuthorContext(NomCode.BOTANICAL, TaxGroup.Plants);
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1850", "Hook."), team("1850", "Hook.f."), plants, Mode.LAX);
    // Cathy J. Hook works on molluscs
    assertEquals(Set.of(WJ_HOOKER), e.decisions().get(0).persons1());
    // Sowerby I died in 1854 and is no author of 1890, Sowerby II is
    Explanation z = matcher(RelativesPolicy.UNKNOWN).explain(team("1890", "Sowerby"), team("1890", "G.B. Sowerby II"), ZOO, Mode.LAX);
    assertEquals(Set.of(SOWERBY2), z.decisions().get(0).persons1());
    assertEquals(Equality.EQUAL, z.verdict());
  }

  /** every candidate ruled out leaves the citation unresolved: the strings decide, never an empty overlap */
  @Test
  public void allRuledOutIsUnresolved() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1990", "Hook.f."), team("1990", "Hooker f."), BOT, Mode.LAX);
    assertEquals(Rule.FALLBACK, e.decisions().get(0).rule());
    assertEquals(Set.of(), e.decisions().get(0).persons1());
    assertEquals(Equality.EQUAL, e.verdict());
  }

  /** two teams are equal when any author of one is any author of the other, the rule of the string comparison */
  @Test
  public void teamRule() {
    var m = matcher(RelativesPolicy.UNKNOWN);
    assertEquals(Equality.EQUAL, m.compareTeams(team(null, "Hook.", "Arn."), team(null, "Arn."), BOT, Mode.LAX));
    assertEquals(Equality.UNKNOWN, m.compareTeams(team(null, "Hook."), team(null, "Hook.f.", "Mill."), BOT, Mode.LAX));
    assertEquals(Equality.DIFFERENT, m.compareTeams(team(null, "L."), team(null, "Hook.f.", "Mill."), BOT, Mode.LAX));
  }

  /** identity does not depend on the mode, only the fallback does */
  @Test
  public void identityIgnoresTheMode() {
    for (Mode mode : Mode.values()) {
      assertEquals(mode.name(), Equality.UNKNOWN,
        matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, mode));
    }
  }

  @Test
  public void listenerSeesEveryComparison() {
    var m = matcher(RelativesPolicy.UNKNOWN);
    m.compareTeams(team(null, "Mill."), team(null, "L."), BOT, Mode.LAX);
    m.compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX);
    assertEquals(List.of(Equality.DIFFERENT, Equality.UNKNOWN), seen.stream().map(Explanation::verdict).toList());
  }

  /**
   * The comparator combines the author verdict with the years: EQUAL years and an UNKNOWN author pair make EQUAL, and an
   * UNKNOWN in a year conflict is DIFFERENT, rules it had before this matcher. Under the UNKNOWN policy the years decide
   * about relatives, and only without years do they stay UNKNOWN.
   */
  @Test
  public void relativesLetTheYearsDecide() {
    var comparator = new AuthorComparator(matcher(RelativesPolicy.UNKNOWN));
    assertEquals(Equality.EQUAL, comparator.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1850", "Hook.f."), NomCode.BOTANICAL));
    assertEquals(Equality.DIFFERENT, comparator.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1855", "Hook.f."), NomCode.BOTANICAL));
    assertEquals(Equality.UNKNOWN, comparator.compare(Authorship.authors("Hook."), Authorship.authors("Hook.f."), NomCode.BOTANICAL));
    var strict = new AuthorComparator(matcher(RelativesPolicy.DIFFERENT));
    assertEquals(Equality.DIFFERENT, strict.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1850", "Hook.f."), NomCode.BOTANICAL));
  }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `mvn -o -pl core test -Dtest='PersonAuthorMatcherTest,PersonRegistryTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: class PersonAuthorMatcher` and `method keys`.

- [ ] **Step 3: Implement**

`PersonRegistry`: record every key per person where `add` indexes it, and expose them:

```java
  private record Keyed(String key, FormCode code) {
  }

  // identity: the persons the registry hands out are its own instances, and records hash all their fields
  private final Map<Person, List<Keyed>> keysByPerson = new IdentityHashMap<>();
```

In `add(String form, Person p, FormCode code)`, inside `if (key != null)` after the existing lines:

```java
      List<Keyed> keys = keysByPerson.computeIfAbsent(p, k -> new ArrayList<>(4));
      Keyed k = new Keyed(key, code);
      if (!keys.contains(k)) {
        keys.add(k);
      }
```

```java
  /**
   * @return the keys of every form of the person whose code applies, derived ones included, as citations are normalized
   */
  public Set<String> keys(Person p, @Nullable NomCode code) {
    Set<String> keys = new LinkedHashSet<>();
    for (Keyed k : keysByPerson.getOrDefault(p, List.of())) {
      if (k.code().appliesTo(code)) {
        keys.add(k.key());
      }
    }
    return keys;
  }
```

`PersonAuthorMatcher`:

```java
package life.catalogue.matching.person;

import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorContext;
import life.catalogue.matching.authorship.AuthorMatcher;
import life.catalogue.matching.authorship.AuthorTeam;

import java.util.*;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Compares authors as persons. Every author of a team resolves to the persons of the registry it may name, narrowed by
 * the year and group of the name. Two authors are EQUAL when they share a person and DIFFERENT when they name unrelated
 * persons; relatives - a parent, a child, a sibling - get the {@link RelativesPolicy}. An author that resolves to
 * nobody falls back to the string comparison, against every form of the persons on the other side. Teams keep the rule
 * of the string comparison: any author of one that is any author of the other makes them EQUAL.
 * <p>
 * Identity does not depend on the mode, only the fallback does.
 */
public class PersonAuthorMatcher implements AuthorMatcher {

  /**
   * What two relatives are. The matcher cannot see recurrence: sources cite the same act under the father in one and
   * the son in the other, and UNKNOWN does not split what they confuse.
   */
  public enum RelativesPolicy {
    UNKNOWN(Equality.UNKNOWN),
    DIFFERENT(Equality.DIFFERENT);

    final Equality verdict;

    RelativesPolicy(Equality verdict) {
      this.verdict = verdict;
    }
  }

  /** what decided a pair of authors */
  public enum Rule {
    /** the two teams are the same strings */
    IDENTICAL,
    /** both authors resolved: a shared person, or unrelated persons */
    IDENTITY,
    /** both resolved, to related persons only */
    RELATIVES,
    /** an author resolved to nobody, the strings decided */
    FALLBACK
  }

  public record Decision(String author1, String author2, Set<Person> persons1, Set<Person> persons2, Rule rule, Equality verdict) {
  }

  public record Explanation(AuthorTeam team1, AuthorTeam team2, AuthorContext context, Equality verdict, List<Decision> decisions) {
  }

  private final PersonRegistry registry;
  private final PersonResolver resolver;
  private final AuthorMatcher fallback;
  private final RelativesPolicy policy;
  @Nullable
  private final Consumer<Explanation> listener;

  /**
   * @param fallback compares the authors no person is known for: the string matcher
   * @param listener sees the explanation of every comparison, for a report; null for none
   */
  public PersonAuthorMatcher(PersonRegistry registry, PersonResolver resolver, AuthorMatcher fallback, RelativesPolicy policy,
                             @Nullable Consumer<Explanation> listener) {
    this.registry = registry;
    this.resolver = resolver;
    this.fallback = fallback;
    this.policy = policy;
    this.listener = listener;
  }

  @Override
  public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    Explanation e = explain(t1, t2, ctx, mode);
    if (listener != null) {
      listener.accept(e);
    }
    return e.verdict();
  }

  /**
   * @return the verdict with what each author resolved to and which rule decided, one decision per author pair compared
   */
  public Explanation explain(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    if (t1.authors().equals(t2.authors())) {
      var d = new Decision(String.join("|", t1.authors()), String.join("|", t2.authors()), Set.of(), Set.of(), Rule.IDENTICAL,
        Equality.EQUAL);
      return new Explanation(t1, t2, ctx, Equality.EQUAL, List.of(d));
    }
    Integer year1 = PersonResolver.year(t1.year());
    Integer year2 = PersonResolver.year(t2.year());
    List<Decision> decisions = new ArrayList<>();
    Equality team = Equality.DIFFERENT;
    for (String a1 : t1.authors()) {
      Set<Person> p1 = resolver.resolve(a1, ctx.code(), year1, ctx.group());
      for (String a2 : t2.authors()) {
        Set<Person> p2 = resolver.resolve(a2, ctx.code(), year2, ctx.group());
        Decision d = decide(a1, p1, a2, p2, ctx, mode);
        decisions.add(d);
        if (d.verdict() == Equality.EQUAL) {
          return new Explanation(t1, t2, ctx, Equality.EQUAL, decisions);
        }
        if (d.verdict() == Equality.UNKNOWN) {
          team = Equality.UNKNOWN;
        }
      }
    }
    return new Explanation(t1, t2, ctx, team, decisions);
  }

  private Decision decide(String a1, Set<Person> p1, String a2, Set<Person> p2, AuthorContext ctx, Mode mode) {
    if (!p1.isEmpty() && !p2.isEmpty()) {
      if (!Collections.disjoint(p1, p2)) {
        return new Decision(a1, a2, p1, p2, Rule.IDENTITY, Equality.EQUAL);
      }
      if (related(p1, p2)) {
        return new Decision(a1, a2, p1, p2, Rule.RELATIVES, policy.verdict);
      }
      return new Decision(a1, a2, p1, p2, Rule.IDENTITY, Equality.DIFFERENT);
    }
    for (String s1 : forms(a1, p1, ctx)) {
      for (String s2 : forms(a2, p2, ctx)) {
        if (fallback.compareTeams(new AuthorTeam(List.of(s1), null), new AuthorTeam(List.of(s2), null), ctx, mode) == Equality.EQUAL) {
          return new Decision(a1, a2, p1, p2, Rule.FALLBACK, Equality.EQUAL);
        }
      }
    }
    return new Decision(a1, a2, p1, p2, Rule.FALLBACK, Equality.DIFFERENT);
  }

  private boolean related(Set<Person> p1, Set<Person> p2) {
    for (Person a : p1) {
      Set<Person> relatives = registry.relatives(a);
      for (Person b : p2) {
        if (relatives.contains(b)) {
          return true;
        }
      }
    }
    return false;
  }

  /** an unresolved author stands for itself, a resolved one also for every form of its persons */
  private Collection<String> forms(String author, Set<Person> persons, AuthorContext ctx) {
    if (persons.isEmpty()) {
      return List.of(author);
    }
    Set<String> forms = new LinkedHashSet<>();
    forms.add(author);
    persons.forEach(p -> forms.addAll(registry.keys(p, ctx.code())));
    return forms;
  }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/life/catalogue/matching/person core/src/test/java/life/catalogue/matching/person
git commit -m "feat(authorship): an author matcher that compares authors as persons

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 8: The relatives fixture

51 hand curated pairs in the pairs format with the `group` column, taken from dataset records and not from either matcher:
- 30 `DIFF` pairs of relatives, each a name authored by one against a name authored by the other: Hook./Hook.f.,
  Rchb./Rchb.f., DC./A.DC./C.DC., L./L.f. and the Presl and Nees brothers from IPNI (2006, no years); the Sowerbys, the
  Adams brothers and the two Sars from WoRMS (2011), in years both published in; the two Geoffroys from ITIS (2144);
- 21 `SAME` pairs citing one of these persons two ways, mined from the classified corpus: `Hook.`/`W.J. Hooker`,
  `C.Presl`/`K.B. Presl`, `G. B. Sowerby II`/`Sowerby` and so on.

The Crouan brothers of the spec are left out: IPNI and WoRMS cite them together only. G. O. Sars comes from names written
`G.O. Sars`: WoRMS' `Sars G.O.` parses as `O.G.Sars` (gbif/name-parser-rust#24). On this fixture the string matcher
judges 15 of the 30 relatives `EQUAL` and 4 of the 21 alias pairs `DIFFERENT`, with and without years.

**Files:**
- Create: `core/src/test/resources/author-corpus/relatives-pairs.tsv`
- Create: `core/src/test/java/life/catalogue/matching/person/RelativesFixtureTest.java`

**Interfaces:**
- Consumes: Task 3's registry, Task 4's pairs format, Task 6 and 7.

- [ ] **Step 1: Create the fixture**

`core/src/test/resources/author-corpus/relatives-pairs.tsv`, tab delimited, exactly these 52 lines:

```tsv
label	source	weight	support	yearAgree	yearConflict	freqA	freqB	intraYearDiff	intraNoYear	intraYearAgree	code	rank	nidx	scientificName	keyA	keyB	datasetKeyA	nameIdA	authorshipA	combAuthorsA	combExA	combYearA	basAuthorsA	basExA	basYearA	sanctioningA	datasetKeyB	nameIdB	authorshipB	combAuthorsB	combExB	combYearB	basAuthorsB	basExB	basYearB	sanctioningB	group
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abies grandis / Abrophyllum ornans	;;;hook	;;;hookf	2006	77236170-1	Hook.	Hook.							2006	790193-1	Hook.f.	Hook.f.							
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abutilon ceratocarpum / Abrotanella inconspicua	;;;hook	;;;hookf	2006	558141-1	Hook.	Hook.							2006	173617-1	Hook.f.	Hook.f.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia decora / Aa argyrolepis	;;;rchb	;;;rchbf	2006	470137-1	Rchb.	Rchb.							2006	614525-1	Rchb.f.	Rchb.f.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acampe intermedia / Aa paleacea	;;;rchb	;;;rchbf	2006	614578-1	Rchb.	Rchb.							2006	614551-1	Rchb.f.	Rchb.f.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia obscura / Abroma molle	;;;adc	;;;dc	2006	471016-1	A.DC.	A.DC.							2006	822015-1	DC.	DC.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia trigona / Absinthium chinense	;;;adc	;;;dc	2006	81980-3	A.DC.	A.DC.							2006	173658-1	DC.	DC.							Eukaryotes
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia obscura / Aglaia barbanthera	;;;adc	;;;cdc	2006	471016-1	A.DC.	A.DC.							2006	576981-1	C.DC.	C.DC.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia trigona / Aglaia bauerlenii	;;;adc	;;;cdc	2006	81980-3	A.DC.	A.DC.							2006	576986-1	C.DC.	C.DC.							Eukaryotes
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Aglaia beccarii / Abroma molle	;;;cdc	;;;dc	2006	576988-1	C.DC.	C.DC.							2006	822015-1	DC.	DC.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abrus precatorius / Acanthus carduifolius	;;;l	;;;lf	2006	469605-1	L.	L.							2006	44848-1	L.f.	L.f.							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Acacia ovata / Acanthus furcatus	;;;l	;;;lf	2006	74889-3	L.	L.							2006	44862-1	L.f.	L.f.							Eukaryotes
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abelmoschus haenkeanus / Agrostis arundinacea	;;;cpresl	;;;jpresl	2006	558013-1	C.Presl	C.Presl							2006	1138503-2	J.Presl	J.Presl							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abelmoschus marianus / Agrostis arundinacea	;;;cpresl	;;;jpresl	2006	558021-1	C.Presl	C.Presl							2006	385357-1	J.Presl	J.Presl							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abildgaardia fusca / Acacia ehrenbergii	;;;nees	;;;tnees	2006	297787-1	Nees	Nees							2006	470234-1	T.Nees	T.Nees							Angiosperms
DIFF	INTRA_NOYEAR	1	0	0	0	1	1	0	1	0	BOTANICAL	SPECIES	0	Abildgaardia indica / Aristolochia officinalis	;;;nees	;;;tnees	2006	297794-1	Nees	Nees							2006	93177-1	T.Nees	T.Nees							Angiosperms
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Amphidesma cancellata / Artemis distans	;;;gbsowerbyi	;;;gbsowerbyii	2011	urn:lsid:marinespecies.org:taxname:724180	G. B. Sowerby I, 1853	G.B.Sowerby I		1853					2011	urn:lsid:marinespecies.org:taxname:538080	G. B. Sowerby II, 1852	G.B.Sowerby II		1852					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Amphidesma reticulata / Artemis lenticularis	;;;gbsowerbyi	;;;gbsowerbyii	2011	urn:lsid:marinespecies.org:taxname:1439493	G. B. Sowerby I, 1853	G.B.Sowerby I		1853					2011	urn:lsid:marinespecies.org:taxname:538090	G. B. Sowerby II, 1852	G.B.Sowerby II		1852					Bivalves
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Astarte subtrigona / Cardium arcuatulum	;;;gbsowerbyii	;;;gbsowerbyiii	2011	urn:lsid:marinespecies.org:taxname:538215	G. B. Sowerby II, 1874	G.B.Sowerby II		1874					2011	urn:lsid:marinespecies.org:taxname:381499	G. B. Sowerby III, 1874	G.B.Sowerby III		1874					Bivalves
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Clausilia platydera / Cardium ornatum	;;;gbsowerbyii	;;;gbsowerbyiii	2011	urn:lsid:marinespecies.org:taxname:1438866	G. B. Sowerby II, 1875	G.B.Sowerby II		1875					2011	urn:lsid:marinespecies.org:taxname:466802	G. B. Sowerby III, 1877	G.B.Sowerby III		1877					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Amphidesma cancellata / Achatina smithi	;;;gbsowerbyi	;;;gbsowerbyiii	2011	urn:lsid:marinespecies.org:taxname:724180	G. B. Sowerby I, 1853	G.B.Sowerby I		1853					2011	urn:lsid:marinespecies.org:taxname:1308138	G. B. Sowerby III, 1890	G.B.Sowerby III		1890					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Acteon elongatus / Arca appendiculata	;;;jdecsowerby	;;;jsowerby	2011	urn:lsid:marinespecies.org:taxname:1023791	J. De C. Sowerby, 1824	J.De C.Sowerby		1824					2011	urn:lsid:marinespecies.org:taxname:1510415	J. Sowerby, 1821	J.Sowerby		1821					
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Anomia striata / Astarte excavata	;;;jdecsowerby	;;;jsowerby	2011	urn:lsid:marinespecies.org:taxname:1510451	J. De C. Sowerby, 1823	J.De C.Sowerby		1823					2011	urn:lsid:marinespecies.org:taxname:1510280	J. Sowerby, 1819	J.Sowerby		1819					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Ampullaria subcarinata / Arca appendiculata	;;;gbsowerbyi	;;;jsowerby	2011	urn:lsid:marinespecies.org:taxname:1442002	G. B. Sowerby I, 1822	G.B.Sowerby I		1822					2011	urn:lsid:marinespecies.org:taxname:1510415	J. Sowerby, 1821	J.Sowerby		1821					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Aetheria tubifera / Astarte excavata	;;;gbsowerbyi	;;;jsowerby	2011	urn:lsid:marinespecies.org:taxname:1425252	G. B. Sowerby I, 1825	G.B.Sowerby I		1825					2011	urn:lsid:marinespecies.org:taxname:1510280	J. Sowerby, 1819	J.Sowerby		1819					Eukaryotes
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Actaeon modestus / Himella fluviatilis	;;;aadams	;;;hadams	2011	urn:lsid:marinespecies.org:taxname:715563	A. Adams, 1855	A.Adams		1855					2011	urn:lsid:marinespecies.org:taxname:539871	H. Adams, 1860	H.Adams		1860					
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Acteon cumingii / Melania mirifica	;;;aadams	;;;hadams	2011	urn:lsid:marinespecies.org:taxname:715537	A. Adams, 1855	A.Adams		1855					2011	urn:lsid:marinespecies.org:taxname:1342976	H. Adams, 1854	H.Adams		1854					Gastropods
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Acroperus angustatus / Amphiura abyssicola	;;;gosars	;;;msars	2011	urn:lsid:marinespecies.org:taxname:1302180	G.O. Sars, 1863	G.O.Sars		1863					2011	urn:lsid:marinespecies.org:taxname:125067	M. Sars, 1861	M.Sars		1861					
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Alona costata / Axinus eumyarius	;;;gosars	;;;msars	2011	urn:lsid:marinespecies.org:taxname:412808	G.O. Sars, 1862	G.O.Sars		1862					2011	urn:lsid:marinespecies.org:taxname:152903	M. Sars, 1870	M.Sars		1870					
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Ateles hybridus / Antilope caama	;;;igeoffroysainthilaire	;;;égeoffroysainthilaire	2144	944186	I. Geoffroy Saint-Hilaire, 1829	I.Geoffroy Saint-Hilaire		1829					2144	1277307	É. Geoffroy Saint-Hilaire, 1803	É.Geoffroy Saint-Hilaire		1803					Chordates
DIFF	INTRA_YEARDIFF	1	0	0	0	1	1	1	0	0	ZOOLOGICAL	SPECIES	0	Felis rubiginosus / Ateles belzebuth	;;;igeoffroysainthilaire	;;;égeoffroysainthilaire	2144	1229687	I. Geoffroy Saint-Hilaire, 1831	I.Geoffroy Saint-Hilaire		1831					2144	572954	É. Geoffroy Saint-Hilaire, 1806	É.Geoffroy Saint-Hilaire		1806					Chordates
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	GENUS	9198	Agave	;;;l	;;;linnaeus	2008	188872	L., 1753	L.		1753					1174	txn:418088	Linnaeus, 1753	Linnaeus		1753					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	17271	Geissanthus	;;;hookf	;;;hookfil	2004	wfo-4000015444	Hook.f.	Hook.f.							1141	6e60d9b6-f717-5e34-b6e4-e3766a1f7aa1	Hook.fil.	Hook.fil.							Angiosperms
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	136872	Heteroneuron	;;;hookf	;;;jdhooker	2008	892920	Hook.f., 1867	Hook.f.		1867					2007	urn:lsid:irmng.org:taxname:1112813	J.D. Hooker in Bentham & J.D. Hooker, 1867	J.D.Hooker		1867					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	2399317	Coryanthes	;;;hook	;;;wjhooker	2008	730999	Hook., 1831	Hook.		1831					2007	urn:lsid:irmng.org:taxname:1270635	W.J. Hooker, 1831	W.J.Hooker		1831					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	18963	Aa	;;;rchbf	;;;rchbfil	2004	wfo-4000000001	Rchb.f.	Rchb.f.							1141	ba291d82-1c36-500a-a0d8-2dbd8b260bf5	Rchb.fil.	Rchb.fil.							Angiosperms
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	19011	Myrosmodes	;;;hgreichenbach	;;;rchbfil	2007	urn:lsid:irmng.org:taxname:1017014	H.G. Reichenbach, 1854	H.G.Reichenbach		1854					1141	0919c3f6-8247-5f10-aee1-4de4107daf38	Rchb.fil.	Rchb.fil.							Angiosperms
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	1249	Blochmannia	;;;hglreichenbach	;;;rchb	2007	urn:lsid:irmng.org:taxname:1385484	H.G.L. Reichenbach in Weigelt, 1828	H.G.L.Reichenbach		1828					2008	455715	Rchb., 1828	Rchb.		1828					Eukaryotes
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	10278	Chromolaena	;;;apdecandolle	;;;dc	2007	urn:lsid:irmng.org:taxname:1070804	A.P. de Candolle, 1836	A.P.de Candolle		1836					2008	532932	DC., 1836	DC.		1836					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	535621	Anodendron	;;;adc	;;;alphdecandolle	2008	445406	A.DC., 1844	A.DC.		1844					2007	urn:lsid:irmng.org:taxname:1102744	Alph. de Candolle, 1844	Alph.de Candolle		1844					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	10520	Duroia	;;;lf	;;;lfil	2004	wfo-4000012778	L.f.	L.f.							1141	f62fff87-ad47-5e8a-9e96-29619c6f82be	L.fil.	L.fil.							Angiosperms
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	17429	Kosteletzkya	;;;cpresl	;;;kbpresl	2008	193747	C.Presl, 1835	C.Presl		1835					2007	urn:lsid:irmng.org:taxname:1042216	K.B. Presl, 1835	K.B.Presl		1835					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	BOTANICAL	GENUS	10559	Endlicheria	;;;cgdnees	;;;nees	2007	urn:lsid:irmng.org:taxname:1088842	C.G.D. Nees, 1833	C.G.D.Nees		1833					2008	628020	Nees, 1833	Nees		1833					Plants
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	FAMILY	10120	Braconidae	;;;nees	;;;neesvonesenbeck	2008	185170	Nees, 1811	Nees		1811					2041	urn:lsid:dyntaxa.se:Taxon:2001163	Nees von Esenbeck, 1812	Nees von Esenbeck		1812					Hymenoptera
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	FAMILY	18461	Trichoniscidae	;;;gosars	;;;sars	2011	urn:lsid:marinespecies.org:taxname:238351	G. O. Sars, 1899	G.O.Sars		1899					1174	txn:475094	Sars, 1899	Sars		1899					Crustacean
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	SPECIES	215732	Agalmopsis elegans	;;;msars	;;;sars	2011	urn:lsid:marinespecies.org:taxname:710723	M. Sars, 1846	M.Sars		1846					2030	208718	Sars, 1846	Sars		1846					OtherAnimals
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	SPECIES	190692	Aetheria tubifera	;;;gbsowerbyi	;;;sowerby	2011	urn:lsid:marinespecies.org:taxname:1425252	G. B. Sowerby I, 1825	G.B.Sowerby I		1825					2144	987812	Sowerby, 1825	Sowerby		1825					Bivalves
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	GENUS	385736	Amarula	;;;gbsowerbyii	;;;sowerby	2007	urn:lsid:irmng.org:taxname:1336439	G. B. Sowerby II, 1842	G.B.Sowerby II		1842					1174	txn:120930	Sowerby, 1842	Sowerby		1842					Gastropods
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	SPECIES	1456920	Calliostoma (Tristichotrochus) aculeatum	;;;gbsowerbyiii	;;;sowerby	2011	urn:lsid:marinespecies.org:taxname:1597259	G. B. Sowerby III, 1912	G.B.Sowerby III		1912					312616	098895a0-b83e-413a-84af-c012e558b2f2	Sowerby, 1912	Sowerby		1912					Gastropods
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	GENUS	145026	Leucotina	;;;aadams	;;;adams	2007	urn:lsid:irmng.org:taxname:1346183	A. Adams, 1860	A.Adams		1860					1174	txn:90261	Adams, 1860	Adams		1860					Gastropods
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	GENUS	17449	Lagothrix	;;;geoffroy	;;;geoffroysainthilaire	2007	urn:lsid:irmng.org:taxname:1222453	Geoffroy, 1812	Geoffroy		1812					2037	ee060f12-65b4-47c4-8f43-90fa91e60925	Geoffroy Saint-Hilaire, 1812	Geoffroy Saint-Hilaire		1812					Eukaryotes
SAME	CROSS	1	1	0	0	1	1	0	0	0	ZOOLOGICAL	GENUS	17449	Lagothrix	;;;geoffroysainthilaire	;;;égeoffroysainthilaire	2037	ee060f12-65b4-47c4-8f43-90fa91e60925	Geoffroy Saint-Hilaire, 1812	Geoffroy Saint-Hilaire		1812					2144	572814	É. Geoffroy Saint-Hilaire in Humboldt, 1812	É.Geoffroy Saint-Hilaire		1812					Eukaryotes
```

- [ ] **Step 2: Write the test**

```java
package life.catalogue.matching.person;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.StringAuthorMatcher;
import life.catalogue.matching.authorship.corpus.AuthorPair;
import life.catalogue.matching.authorship.corpus.ConfusionMatrix;
import life.catalogue.matching.authorship.corpus.CorpusEvaluator;
import life.catalogue.matching.authorship.corpus.CorpusIO;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Relatives the corpus hardly holds as DIFF, since a dataset rarely lists both: the fixture pins what both matchers do.
 * Like CorpusEvaluatorTest.knownMisjudgements it is a characterisation, not a statement of what is right: whoever
 * moves the numbers moves them here, which is the point.
 */
public class RelativesFixtureTest {
  static final String FIXTURE = "author-corpus/relatives-pairs.tsv";
  private static List<AuthorPair> pairs;

  @BeforeClass
  public static void load() throws Exception {
    pairs = CorpusIO.readPairs(Resources.toFile(FIXTURE));
  }

  static AuthorComparator persons(RelativesPolicy policy) {
    PersonRegistry reg = PersonRegistry.get();
    return new AuthorComparator(new PersonAuthorMatcher(reg, new PersonResolver(reg, PersonResolver.Margins.DEFAULT),
      new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE), policy, null));
  }

  static ConfusionMatrix matrix(AuthorComparator comparator, boolean withYears) {
    return new CorpusEvaluator(comparator).evaluate(pairs).matrix(CorpusEvaluator.ALL, withYears);
  }

  @Test
  public void fixture() {
    assertEquals(51, pairs.size());
    assertEquals(30, pairs.stream().filter(p -> p.label() == Label.DIFF).count());
  }

  @Test
  public void strings() {
    for (boolean years : new boolean[]{true, false}) {
      ConfusionMatrix m = matrix(new AuthorComparator(AuthorshipNormalizer.INSTANCE), years);
      assertEquals(15, m.count(Label.DIFF, Equality.EQUAL));
      assertEquals(15, m.count(Label.DIFF, Equality.DIFFERENT));
      assertEquals(17, m.count(Label.SAME, Equality.EQUAL));
      assertEquals(4, m.count(Label.SAME, Equality.DIFFERENT));
    }
  }

  /**
   * Relatives are UNKNOWN, which the comparator combines with the years: only G. B. Sowerby II and III, both cited 1874,
   * come out EQUAL; years a few apart make DIFFERENT and the botanical pairs without years stay UNKNOWN.
   */
  @Test
  public void personsRelativesUnknown() {
    ConfusionMatrix m = matrix(persons(RelativesPolicy.UNKNOWN), true);
    assertEquals(1, m.count(Label.DIFF, Equality.EQUAL));
  }

  @Test
  public void personsRelativesDifferent() {
    ConfusionMatrix m = matrix(persons(RelativesPolicy.DIFFERENT), true);
    assertEquals(0, m.count(Label.DIFF, Equality.EQUAL));
  }

  /** prints every verdict of all three, for the Outcome and for whoever moves the numbers */
  public static void main(String[] args) throws Exception {
    load();
    var strings = new CorpusEvaluator(new AuthorComparator(AuthorshipNormalizer.INSTANCE)).evaluate(pairs).verdicts();
    var unknown = new CorpusEvaluator(persons(RelativesPolicy.UNKNOWN)).evaluate(pairs).verdicts();
    var different = new CorpusEvaluator(persons(RelativesPolicy.DIFFERENT)).evaluate(pairs).verdicts();
    System.out.println("label  strings    unknown    different  | authorships");
    for (int i = 0; i < pairs.size(); i++) {
      AuthorPair p = pairs.get(i);
      System.out.printf("%-6s %-10s %-10s %-10s | %s | %s%n", p.label(), strings.get(i).verdict(), unknown.get(i).verdict(),
        different.get(i).verdict(), p.a().authorship(), p.b().authorship());
    }
  }
}
```

- [ ] **Step 3: Run it and pin the person numbers**

Run: `mvn -o -pl core test -Dtest=RelativesFixtureTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 4 tests. The person tests hold one expectation each so far, which follows from the design: every
relative of the fixture resolves (Task 3 checks the hard ones), so under the `DIFFERENT` policy no pair of relatives is
`EQUAL`, and under `UNKNOWN` only the one whose years agree. If either fails, print the verdicts, find which citation
resolved to whom, and fix the cause before pinning anything. Then print all verdicts:

```bash
cd core
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-cp %classpath life.catalogue.matching.person.RelativesFixtureTest"
cd ..
```

Pin the observed counts: in each person test assert all six cells of the matrix with years (`DIFF` and `SAME` against
`EQUAL`, `DIFFERENT`, `UNKNOWN`), in the style of `strings()`, keeping the first expectation. Add a comment line per test
naming the pairs that are not what the label says and why, read from the printout: an unresolved citation, a missing
relation, agreeing years. Ledger anything unexpected as a ruling; a wrong verdict here is a finding for the Outcome, not
a reason to change the matcher in this task.

- [ ] **Step 4: Run it to see it pass and commit**

Run: `mvn -o -pl core test -Dtest=RelativesFixtureTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 4 tests, now with every cell pinned.

```bash
git add core/src/test/resources/author-corpus/relatives-pairs.tsv core/src/test/java/life/catalogue/matching/person/RelativesFixtureTest.java
git commit -m "test(authorship): a fixture of relatives that pins both matchers

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 9: PersonCorpusReport

Runs the corpus report with the person matcher under both relatives policies and adds what the spec asks for:
- the share of citations resolved;
- the verdicts decided by identity, by the fallback and by the relatives policy;
- the unresolved citations ranked by names, the worklist of missing persons;
- every pair the relatives policy decided;
- run time and heap with the registry loaded;
- the flips against the string verdicts.

**Files:**
- Create: `core/src/test/java/life/catalogue/matching/person/PersonCorpusReport.java`
- Test: `core/src/test/java/life/catalogue/matching/person/PersonCorpusReportTest.java`

**Interfaces:**
- Consumes: `AuthorCorpusReport.report(File, File, AuthorComparator, List<String>, AuthorshipNormalizer, Extension)`,
  `AuthorCorpusReport.REPORT`/`VERDICTS`, `AuthorVerdictDiff.read/diff/render`, Tasks 6 and 7.
- Produces: `PersonCorpusReport.report(File pairs, @Nullable File stringVerdicts, File outDir, Margins margins)`, writing
  `<outDir>/unknown/` and `<outDir>/different/`, each with `report.txt`, `verdicts.tsv.gz` and, given string verdicts,
  `diff.txt`; `PersonCorpusReport.margins(String[] args, int from)`.

- [ ] **Step 1: Write the failing test**

```java
package life.catalogue.matching.person;

import life.catalogue.common.io.Resources;
import life.catalogue.matching.authorship.corpus.AuthorCorpusReport;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PersonCorpusReportTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** both policies on the relatives fixture: the person sections, and the flips against the string verdicts */
  @Test
  public void reportsBothPolicies() throws Exception {
    File pairs = Resources.toFile(RelativesFixtureTest.FIXTURE);
    File strings = tmp.newFolder("strings");
    AuthorCorpusReport.report(pairs, strings, true);
    File out = tmp.newFolder("persons");
    PersonCorpusReport.report(pairs, new File(strings, AuthorCorpusReport.VERDICTS), out, PersonResolver.Margins.DEFAULT);
    for (String policy : new String[]{"unknown", "different"}) {
      String r = Files.readString(new File(out, policy + "/" + AuthorCorpusReport.REPORT).toPath());
      assertTrue(r, r.contains("comparator: persons, relatives " + policy.toUpperCase()));
      assertTrue(r, r.contains("## What decided the verdicts"));
      assertTrue(r, r.contains("## Citations resolved"));
      assertTrue(r, r.contains("## Citations no person resolves"));
      assertTrue(r, r.contains("## Pairs the relatives policy decided"));
      assertTrue(r, r.contains("Hook. | Hook.f."));
      assertTrue(r, r.contains("## Run"));
      assertTrue(new File(out, policy + "/diff.txt").isFile());
    }
  }

  /** unset margins keep their default, which Task 10 may move */
  @Test
  public void margins() {
    var d = PersonResolver.Margins.DEFAULT;
    assertEquals(new PersonResolver.Margins(d.minAge(), 50, d.activeSlack()),
      PersonCorpusReport.margins(new String[]{"p", "v", "o", "--posthumous", "50"}, 3));
    assertEquals(new PersonResolver.Margins(12, d.posthumous(), 5),
      PersonCorpusReport.margins(new String[]{"p", "v", "o", "--min-age", "12", "--active-slack", "5"}, 3));
  }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn -o -pl core test -Dtest=PersonCorpusReportTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol: class PersonCorpusReport`.

- [ ] **Step 3: Implement**

```java
package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.StringAuthorMatcher;
import life.catalogue.matching.authorship.corpus.AuthorCorpusReport;
import life.catalogue.matching.authorship.corpus.AuthorPair;
import life.catalogue.matching.authorship.corpus.AuthorVerdictDiff;
import life.catalogue.matching.authorship.corpus.CorpusEvaluator.Verdict;
import life.catalogue.matching.person.PersonAuthorMatcher.Decision;
import life.catalogue.matching.person.PersonAuthorMatcher.Explanation;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;
import life.catalogue.matching.person.PersonAuthorMatcher.Rule;
import life.catalogue.matching.person.PersonResolver.Margins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Measures the person matcher on the corpus: the report of {@link AuthorCorpusReport} under both relatives policies, plus
 * which rule decided the verdicts, how many citations resolved, the citations no person resolves ranked by names, every
 * pair the relatives policy decided, and the flips against the string matcher's verdicts. See docs/AUTHOR-CORPUS.md.
 * <pre>
 * mvn -q -pl dao -am install -DskipTests
 * cd core
 * mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx6g -cp %classpath life.catalogue.matching.person.PersonCorpusReport \
 *     ../dao/target/author-corpus/classified/pairs.tsv.gz ../dao/target/author-corpus/classified/verdicts.tsv.gz \
 *     target/person-corpus [--min-age N] [--posthumous N] [--active-slack N]"
 * </pre>
 */
public class PersonCorpusReport {
  static final String DIFF = "diff.txt";
  private static final int TOP = 200;

  /** attributes the explanations of the matcher to the pair being judged */
  static class Explaining implements AuthorCorpusReport.Extension, Consumer<Explanation> {
    private final long start = System.nanoTime();
    private final List<Explanation> current = new ArrayList<>();
    private final Map<String, long[]> decidedBy = new TreeMap<>();
    private final Map<String, Long> unresolved = new HashMap<>();
    private final List<Map.Entry<Integer, String>> relatives = new ArrayList<>();
    private long citations;
    private long resolved;
    private long citationNames;
    private long resolvedNames;

    @Override
    public void accept(Explanation e) {
      current.add(e);
    }

    @Override
    public void before(AuthorPair pair) {
      current.clear();
    }

    @Override
    public void after(Verdict v) {
      AuthorPair p = v.pair();
      Set<Rule> rules = EnumSet.noneOf(Rule.class);
      Set<String> known = new TreeSet<>();
      Set<String> unknown = new TreeSet<>();
      List<String> related = new ArrayList<>();
      for (Explanation e : current) {
        for (Decision d : e.decisions()) {
          rules.add(d.rule());
          if (d.rule() == Rule.IDENTICAL) continue;
          (d.persons1().isEmpty() ? unknown : known).add(d.author1());
          (d.persons2().isEmpty() ? unknown : known).add(d.author2());
          if (d.rule() == Rule.RELATIVES) {
            related.add(ids(d.persons1()) + " / " + ids(d.persons2()));
          }
        }
      }
      unknown.removeAll(known);
      String key = (rules.isEmpty() ? "years alone" : rules.stream().map(Enum::name).collect(Collectors.joining("+")))
        + " -> " + v.verdict();
      long[] n = decidedBy.computeIfAbsent(key, k -> new long[2]);
      n[0]++;
      n[1] += p.weight();
      citations += known.size() + unknown.size();
      resolved += known.size();
      citationNames += (long) (known.size() + unknown.size()) * p.weight();
      resolvedNames += (long) known.size() * p.weight();
      unknown.forEach(c -> unresolved.merge(c, (long) p.weight(), Long::sum));
      if (!related.isEmpty()) {
        relatives.add(Map.entry(p.weight(), String.format("%6d  %-5s %-9s %s | %s   [%s]", p.weight(), p.label(), v.verdict(),
          p.a().authorship(), p.b().authorship(), String.join(", ", related))));
      }
    }

    private static String ids(Set<Person> persons) {
      return persons.stream().map(Person::id).collect(Collectors.joining(","));
    }

    @Override
    public void render(StringBuilder sb) {
      sb.append("\n## What decided the verdicts\nThe rules of every author pair a name pair needed, and the verdict.\n");
      decidedBy.forEach((k, n) -> sb.append(String.format("  %-40s %,9d pairs %,11d names%n", k, n[0], n[1])));
      sb.append(String.format("%n## Citations resolved%n  %.1f%% of %,d citations, %.1f%% weighted by names%n",
        pct(resolved, citations), citations, pct(resolvedNames, citationNames)));
      sb.append(String.format("%n## Citations no person resolves%n%,d citations, the first %d by names:%n", unresolved.size(), TOP));
      unresolved.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(TOP)
        .forEach(e -> sb.append(String.format("  %9d  %s%n", e.getValue(), e.getKey())));
      sb.append(String.format("%n## Pairs the relatives policy decided%n%,d pairs, the first %d by names:%n", relatives.size(), TOP));
      relatives.stream().sorted(Map.Entry.<Integer, String>comparingByKey().reversed()).limit(TOP)
        .forEach(e -> sb.append("  ").append(e.getValue()).append('\n'));
      sb.append(String.format("%n## Run%n  %,d s%n", (System.nanoTime() - start) / 1_000_000_000L));
    }

    private static double pct(long part, long total) {
      return total == 0 ? 0 : 100d * part / total;
    }
  }

  public static void report(File pairs, @Nullable File stringVerdicts, File outDir, Margins margins) throws IOException {
    long t0 = System.nanoTime();
    PersonRegistry registry = PersonRegistry.get();
    long loadMs = (System.nanoTime() - t0) / 1_000_000;
    System.gc();
    long heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
    var strings = new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE);
    for (RelativesPolicy policy : RelativesPolicy.values()) {
      File dir = new File(outDir, policy.name().toLowerCase(Locale.ROOT));
      var explaining = new Explaining();
      var matcher = new PersonAuthorMatcher(registry, new PersonResolver(registry, margins), strings, policy, explaining);
      AuthorCorpusReport.report(pairs, dir, new AuthorComparator(matcher), List.of(
        "comparator: persons, relatives " + policy,
        String.format("registry: %,d persons, loaded in %,d ms, heap in use after loading %,d MB", registry.size(), loadMs, heapMb),
        "margins: " + margins), null, explaining);
      if (stringVerdicts != null) {
        var diff = AuthorVerdictDiff.diff(AuthorVerdictDiff.read(stringVerdicts),
          AuthorVerdictDiff.read(new File(dir, AuthorCorpusReport.VERDICTS)));
        Files.writeString(new File(dir, DIFF).toPath(), AuthorVerdictDiff.render(diff), StandardCharsets.UTF_8);
      }
    }
  }

  static Margins margins(String[] args, int from) {
    int minAge = Margins.DEFAULT.minAge();
    int posthumous = Margins.DEFAULT.posthumous();
    int activeSlack = Margins.DEFAULT.activeSlack();
    for (int i = from; i + 1 < args.length; i += 2) {
      int v = Integer.parseInt(args[i + 1]);
      switch (args[i]) {
        case "--min-age" -> minAge = v;
        case "--posthumous" -> posthumous = v;
        case "--active-slack" -> activeSlack = v;
        default -> throw new IllegalArgumentException("Unknown option " + args[i]);
      }
    }
    return new Margins(minAge, posthumous, activeSlack);
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 3 || !new File(args[0]).isFile()) {
      System.err.println("Usage: PersonCorpusReport <pairs> <string verdicts> <output directory> "
        + "[--min-age N] [--posthumous N] [--active-slack N]");
      System.exit(1);
    }
    File outDir = new File(args[2]);
    report(new File(args[0]), new File(args[1]), outDir, margins(args, 3));
    for (RelativesPolicy policy : RelativesPolicy.values()) {
      File dir = new File(outDir, policy.name().toLowerCase(Locale.ROOT));
      System.out.println(Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath()).lines().limit(25)
        .collect(Collectors.joining("\n")));
      System.out.println("Full report in " + dir);
    }
  }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `mvn -o -pl core test -Dtest=PersonCorpusReportTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 2 tests. If `Hook. | Hook.f.` is missing from the relatives section, read the report: the fixture pins
that pair as a relatives decision, so its absence is a bug here.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/life/catalogue/matching/person/PersonCorpusReport.java core/src/test/java/life/catalogue/matching/person/PersonCorpusReportTest.java
git commit -m "test(authorship): the person matcher measured on the corpus under both relatives policies

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 10: Margins, evaluation and Outcome

Set the margins on the corpus, run the evaluation, review what regressed and write the numbers into the design record.

**Files:**
- Modify: `core/src/main/java/life/catalogue/matching/person/PersonResolver.java` (`Margins.DEFAULT`, only if the corpus
  says so)
- Modify: `core/src/test/java/life/catalogue/matching/person/RelativesFixtureTest.java` (re-pinned if `DEFAULT` moved)
- Modify: `docs/2026-09-23-person-author-comparison.md` (Status, Outcome), `docs/AUTHOR-PERSONS.md`, `docs/AUTHOR-CORPUS.md`

- [ ] **Step 1: Run the default margins**

```bash
mvn -o -q -pl dao -am install -DskipTests
cd core
mvn -o -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx6g -cp %classpath life.catalogue.matching.person.PersonCorpusReport ../dao/target/author-corpus/classified/pairs.tsv.gz ../dao/target/author-corpus/classified/verdicts.tsv.gz target/person-corpus/default"
```

Expected: `target/person-corpus/default/{unknown,different}/report.txt` and `diff.txt`. Note the run time and heap.

- [ ] **Step 2: Vary the margins one at a time**

Run the same command four times more with, in turn, `--posthumous 5`, `--posthumous 50`, `--active-slack 5` and
`--active-slack 30`, each into its own directory (`target/person-corpus/posthumous-5` and so on). Tabulate for each run
and policy the weighted share of `SAME` judged `EQUAL` and of `DIFF` judged `DIFFERENT` (ALL, with years), next to the
string baseline of `../dao/target/author-corpus/classified/report.txt`.

- [ ] **Step 3: Choose the margins**

Keep `Margins.DEFAULT` unless a variant raises `DIFF` judged `DIFFERENT` without lowering `SAME` judged `EQUAL`, weighted by
names, under the `UNKNOWN` policy. If one does, set `DEFAULT` to it and ledger the choice as a ruling with the table.
Then rerun `RelativesFixtureTest`; re-pin its person counts if they moved. Run
`mvn -o -pl core test -Dtest='life.catalogue.matching.person.**' -Dsurefire.failIfNoSpecifiedTests=false` and expect
all to pass.

- [ ] **Step 4: Review the regressions**

For each policy, read `diff.txt` of the chosen run. Look at every regressed pair backed by 10 or more names: its
authorships, what the report says each side resolved to, and whether the label or the person verdict is right. Sort
them into causes: a wrong or missing person, a missing relation, a parse defect, agreeing years of relatives, a label
that is wrong. Keep the counts per cause and three examples of each.

- [ ] **Step 5: Write the Outcome**

In `docs/2026-09-23-person-author-comparison.md`:
- Status: "Phases 0 to 3 are implemented on branch `feat/person-author-comparison`, not merged."
- A **Phase 3** paragraph in the Outcome with:
  - the confusion numbers of the string matcher and of the person matcher under both policies (ALL, BOTANICAL,
    ZOOLOGICAL, weighted by names);
  - the relatives fixture numbers of all three;
  - the resolved share;
  - what decided the verdicts;
  - the top of the unresolved worklist;
  - run time and heap;
  - the regression review by cause;
  - the margins chosen and their table;
  - whether the person matcher is better by the spec's measure, stated plainly.
- The deviations from this plan and from the spec, among them:
  - the `mode` parameter of `explain`;
  - the Crouan brothers left out;
  - the fixture's alias pairs taken from all corpus datasets;
  - the way the comparator's `Equality.and` lets the years decide about relatives under the `UNKNOWN` policy.

`docs/AUTHOR-PERSONS.md`: a section "Comparing authors as persons" describing `PersonResolver` (narrowing, margins) and
`PersonAuthorMatcher` (rules, policies, fallback, team rule, the years rule of the comparator), current behaviour only.

`docs/AUTHOR-CORPUS.md`: a section on `PersonCorpusReport` (command, output directories, what its sections mean).

- [ ] **Step 6: Commit**

```bash
cd ..
git add core/src docs/2026-09-23-person-author-comparison.md docs/AUTHOR-PERSONS.md docs/AUTHOR-CORPUS.md
git commit -m "docs(authorship): phase 3 measured - the person matcher against the string matcher

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```
