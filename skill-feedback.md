# Etendo Dev Assistant — Skill Feedback

## Issues

### F1: [_context] — context.json javapackage does not match the real module
- **What happened:** `.etendo/context.json` has `"module": "com.etendoerp.etendogo"`, but resolving `AD_MODULE_ID` from that javapackage returns nothing. The real module owning the `ETGO` DB prefix (and the `ETGO_SF_*` tables) is `com.etendoerp.go` (`AD_MODULE_ID = 94E1B433CF55451EABB764750AC5902A`).
- **What was expected:** The javapackage in context.json should resolve directly to the module via `SELECT ad_module_id FROM ad_module WHERE javapackage = ...`.
- **How it was resolved:** Resolved the module by DB prefix instead: `SELECT m.javapackage, m.ad_module_id FROM ad_module_dbprefix p JOIN ad_module m ON m.ad_module_id=p.ad_module_id WHERE p.name='ETGO';`
- **Affected skill:** etendo-_context, etendo-alter-db
- **Suggestion:** When the context.json javapackage doesn't resolve, fall back to resolving by DB prefix before asking the user. Consider correcting context.json to `com.etendoerp.go`.
- **Date:** 2026-06-11

### F2: [_guidelines / alter-db] — SyncTerms fails globally with ad_menu not-null violation
- **What happened:** The mandatory post-creation `SyncTerms` webhook failed with `{"error":"Error: - null value in column \"name\" of relation \"ad_menu\" violates not-null constraint"}`. This is a global AD-wide failure unrelated to the new table; afterwards `SELECT count(*) FROM ad_menu WHERE name IS NULL` = 0 (the failed run rolled back). As a side effect, `AD_ELEMENT` records for the new columns were never created/linked (SyncTerms is what normally creates them).
- **What was expected:** SyncTerms creates/links `AD_ELEMENT` rows for the table's columns.
- **How it was resolved:** Created the missing elements manually via SQL. **Key gotcha:** `ad_element_mod_trg` raises `@20533@` ("Cannot insert/delete objects in a module not in development") if the new element lands in core module `'0'`. The INSERT must set `ad_module_id` to the (in-development) owning module. Columns whose name matches an existing shared core element (status, description, checksum) link to those automatically — leave them. Then link via `UPDATE ad_column SET ad_element_id = (SELECT ... WHERE LOWER(columnname)=LOWER(...))`.
- **Affected skill:** etendo-alter-db (Step 6 element-sync SQL), _guidelines section 12
- **Suggestion:** The element-sync SQL in the alter-db skill omits `ad_module_id` on the INSERT, so it fails for any module-owned (non-core) column due to `ad_element_mod_trg`. Add `ad_module_id` (the owning module, which must be in development) to the INSERT column list. Also document that SyncTerms can fail globally on a pre-existing `ad_menu` null-name row and that the manual element-sync is the fallback.
- **Date:** 2026-06-11

### F3: [alter-db / CreateColumn.java] — TABLEDIR_REFERENCE_ID is "17", but 17 is List and 19 is TableDir
- **What happened:** Created a TableDir column (`referenceID: "19"`) for a parent FK. The column was created and registered, but **no physical FK was ever added** and the response carried no hint — `handleFKCase` simply never ran. Root cause: `CreateColumn.java:42` declares `TABLEDIR_REFERENCE_ID = "17"`, and `isTableDirRef()` compares `reference.getId()` against it. Verified against the instance: `SELECT ad_reference_id, name FROM ad_reference WHERE ad_reference_id IN ('17','18','19','30')` → 17=List, 18=Table, 19=TableDir, 30=Search.
- **Two consequences, both silent:**
  1. Every TableDir column (ref 19) loses its physical FK, and skips `validateTableDir()` and the `COPDEV_ExternalTableDirRef` guard.
  2. Every List column (ref 17) is *mistaken* for a TableDir, so `handleFKCase` strips the last 3 characters off the column name and tries `REFERENCES <thatname>` — an FK to a table that does not exist.
- **How it was resolved:** Fixed the constant to `"19"` and added the FK by hand for the already-created column.
- **Affected skill:** etendo-alter-db (Step 5), `CreateColumn.java:42`
- **Date:** 2026-09-02

### F4: [alter-db / CreateColumn.java] — canBeNull never reaches AD_COLUMN.ismandatory
- **What happened:** Passed `"canBeNull": "false"` for three columns. The physical columns were correctly `NOT NULL`, but all three landed in AD with `ismandatory = 'N'`. `canBeNull` is consumed **only** by the DDL builder (`addColumn`, `queryNull = canBeNull ? " " : " NOT NULL"`); `newCol.setMandatory(...)` is never called.
- **Why it matters more than it looks:** AD_COLUMN is what `export.database` writes into the model XML. AD saying "not mandatory" over a physical `NOT NULL` column means the exported XML says `required="false"`, and the next `update.database` can drop the constraint. The divergence is invisible until it silently relaxes the schema.
- **How it was resolved:** `newCol.setMandatory(!nullable)`, plus `UPDATE ad_column SET ismandatory='Y'` for the columns already created.
- **Affected skill:** etendo-alter-db (Step 5), `CreateColumn.java`
- **Date:** 2026-09-02

### F5: [alter-db / CreateColumn.java] — canBeNull="Y" means NOT NULL (backwards)
- **What happened:** The skill documents `canBeNull` as accepting `"true"/"false"` **or** `"Y"/"N"`. The code only tests `equalsIgnoreCase(canBeNull, "true")`, so `"Y"` — which means "yes, it can be null" — evaluates to false and produces a `NOT NULL` column. Exactly inverted for anyone following the documented Y/N form.
- **How it was resolved:** Added `parseNullable()` accepting true/y/yes/1, defaulting to nullable when the parameter is absent (an unwanted NOT NULL blocks every insert; an unwanted NULL does not).
- **Affected skill:** etendo-_webhooks (CreateColumn section), `CreateColumn.java`
- **Date:** 2026-09-02

### F6: [alter-db / CreateColumn.java] — no way to specify a column's length
- **What happened:** Needed `VARCHAR(60)` and `VARCHAR(255)` columns to mirror an existing table for a lossless migration. `CreateColumn` takes no length parameter: ref 10 is hardcoded to `VARCHAR(200)` via `getDbType()`. 200 silently **narrows** a 255-char source column, which is data loss, not a cosmetic mismatch.
- **How it was resolved:** Added an optional `length` parameter and an `addColumn(..., Integer lengthOverride)` overload, applied only to length-bearing types (so a timestamp can never become `timestamp(19)`). Old signature kept delegating, so existing callers are untouched.
- **Affected skill:** etendo-alter-db (Step 5), `CreateColumn.java`
- **Date:** 2026-09-02

### F7: [alter-db / CreateColumn.java] — date/timestamp columns are created with fieldlength = 0
- **What happened:** Columns created with ref 15 (Date) landed with `AD_COLUMN.fieldlength = 0`. The skill's own docs say a fieldlength of 0 makes the field uneditable and recommend 19 for Date/DateTime — yet the webhook produces 0 for every type whose `getDbType()` mapping carries no length (timestamps, numerics, text).
- **Why it cannot be fixed in the mapping:** `getDbType()` feeds **both** the DDL string and `AD_COLUMN.length`. Adding 19 to the timestamp entry would emit `timestamp without time zone(19)` and break the DDL. The two have to be decoupled.
- **How it was resolved:** Added `adFieldLength(dbTypeName, ddlLength)`, which uses the DDL length when there is one and otherwise falls back per kind (19 timestamp, 10 numeric, 2000 text) — never 0.
- **Affected skill:** etendo-alter-db (Step 5 fieldlength table), `CreateColumn.java`
- **Date:** 2026-09-02

### F8: [alter-db / SyncTerms.java] — the element cleanup is dead code (Restrictions.eq vs eqProperty)
- **What happened:** `SyncTerms` intends to clean up elements whose name is still the raw DB column name. It builds the filter as `Restrictions.eq(Element.PROPERTY_NAME, Element.PROPERTY_DBCOLUMNNAME)`. `Restrictions.eq` compares a property against a literal **value**, and the second argument here is the *name* of another property, so the generated SQL is `name = 'columnName'` — matching nothing but an element literally named "columnName". Comparing two properties requires `Restrictions.eqProperty`.
- **Effect:** the whole cleanup loop has never run for any element, since it was written.
- **How it was resolved:** Switched both comparisons to `eqProperty`.
- **Affected skill:** etendo-alter-db (Step 6), `SyncTerms.java`
- **Date:** 2026-09-02

### F9: [alter-db] — documented parameters do not match what the hooks require
- **What happened:** Following Step 6 verbatim, two of the three mandatory post-creation hooks rejected the documented payload:
  - `CheckTablesColumnHook` with `{"TableID": ...}` → `Missing parameter: "ModuleID"`. Works with `{"TableID","ModuleID"}`.
  - `ElementsHandler` with `{"TableID", "Mode"}` → `Missing parameter: "Name"`. Works with `{"TableID","ModuleID","Name","Mode"}`.
- **Affected skill:** etendo-alter-db (Step 6 and the header snippet, which both show `TableID` alone)
- **Suggestion:** Update both snippets. `TABLE_ID is required` in the header note is incomplete — `ModuleID` is required too.
- **Date:** 2026-09-02

### F10: [alter-db] — the element sync must run AFTER CheckTablesColumnHook, and the skill does not say so
- **What happened:** Ran the Step 6 element-sync SQL before `CheckTablesColumnHook`. Result: the six business columns got their elements, but the eight audit columns (`AD_Client_ID`, `Created`, `Isactive`, …) ended up with `ad_element_id IS NULL`. Reason: `CreateAndRegisterTable` creates the physical audit columns but **not** their `AD_COLUMN` rows — `CheckTablesColumnHook` is what creates those, and (as the skill notes) it does not create elements. So any element sync run before it cannot see them.
- **How it was resolved:** Re-ran the insert+link after the hook: 1 element created, 8 columns linked, 0 remaining.
- **Affected skill:** etendo-alter-db (Step 6)
- **Suggestion:** State the ordering explicitly — `CheckTablesColumnHook` first, element sync second — and mention that a fresh table needs the sync for its audit columns, not just its business ones.
- **Date:** 2026-09-02

### F11: [alter-db / JavaPackageRetriever] — cannot find a module by its exact javapackage
- **What happened:** `{"Name":"com.etendoerp.go"}` → `Missing parameter: "KeyWord"` (the skill's table calls this webhook "Find module by name" and does not document the parameter). Retrying with `{"KeyWord":"com.etendoerp.go"}` → `{"info":""}`, empty, even though that javapackage exists (`AD_MODULE_ID = 94E1B433CF55451EABB764750AC5902A`). Same symptom as F1 from a different angle.
- **How it was resolved:** Queried `ad_module` directly.
- **Affected skill:** etendo-_webhooks (webhook table), `JavaPackageRetriever.java`
- **Suggestion:** Document the `KeyWord` parameter, and make an exact javapackage match work — matching the DB prefix would also help (see F1).
- **Date:** 2026-09-02

### F12: [alter-db / CreateAndRegisterTable] — the documented JavaClass value produces a doubled package
- **What happened:** Step 4 of the alter-db skill documents the payload field as
  `"JavaClass": "{javapackage}.data.{EntityName}"` — a fully-qualified name. That value is stored
  verbatim in `AD_TABLE.CLASSNAME`, but AD expects the **bare class name** there; the package comes
  from `AD_PACKAGE.JAVAPACKAGE`. Entity generation therefore concatenates the two and emits
  ```
  src-gen/com/etendoerp/go/schemaforge/data/com/etendoerp/go/schemaforge/data/AccountIdentity.java
  package com.etendoerp.go.schemaforge.data.com.etendoerp.go.schemaforge.data;
  ```
  The entity exists, but not under the name anyone would import, so it is unusable via DAL.
- **How widespread it already is:** in `com.etendoerp.go`, 8 of 19 `ETGO%` tables carry an FQN in
  `CLASSNAME` — precisely the ones created through this webhook (`etgo_oauth2_client`,
  `etgo_oauth2_token`, `etgo_fiscal_decl_incident`, `etgo_data_fix_history`, the three
  `etgo_survey_*`, and the new `etgo_account_identity`). The 11 hand-made tables all carry a bare
  name (`ETGO_Account` → `Account`). The defect went unnoticed because none of those 8 entities is
  imported anywhere — the code reaches those tables another way.
- **How it was resolved:** `UPDATE ad_table SET classname='AccountIdentity' WHERE ...`, deleted the
  stale generated file, re-exported and regenerated. The other 7 were left alone: they are unused,
  and changing a live table's classname is a separate, riskier cleanup.
- **Affected skill:** etendo-alter-db (Step 4 payload example)
- **Suggestion:** Document `JavaClass` as the bare entity name, and have
  `CreateAndRegisterTable` defend itself — if the value contains a dot, strip everything up to the
  last one (or reject it) rather than storing an FQN that only fails later, at generation time, in
  a directory nobody looks at.
- **Date:** 2026-09-02

### F13: [alter-db / CreateColumn.java] — Search references (30) never get a physical FK, and that is the reference the skill mandates for extension columns
- **What happened:** `handleFKCase` only acts when the reference is TableDir or Table (`isTableDirRef || isTableBaseRef`). Reference **30 (Search) is not handled at all**, so no foreign key is ever created for it. This is not an edge case: the skill explicitly instructs using Search for extension columns, because TableDir is rejected there (`COPDEV_ExternalTableDirRef`). So **every extension column created through this webhook is born without a foreign key**, by design rather than by accident.
- **Evidence:** `export.database` on `com.etendoerp.go` reports three `NOT_PART_OF_FOREIGN_KEY` warnings, and all three columns are reference 30 owned by that module: `ETGO_SUPPORT_CONVERSATION.AD_User_ID`, `C_INVOICELINE.EM_Etgo_Source_Invoiceline_ID`, `M_INOUT.EM_ETGO_Currency_ID`.
- **Why it was not simply implemented:** a Search reference can point at any table, and unlike TableDir the target is not derivable from the column name — `EM_ETGO_Currency_ID` targets `C_Currency`, `EM_Etgo_Source_Invoiceline_ID` targets `C_InvoiceLine`. Name-stripping cannot work here.
- **Suggestion:** accept an optional `fkTargetTable` parameter and create the constraint when it is supplied. Failing that, **say so in the response** — a message like "no FK created for a Search reference; add it manually" would have surfaced this at creation time instead of at the next export, months later. Silence is the actual defect.
- **Affected skill:** etendo-alter-db (Step 5, extension-column section), `CreateColumn.java` `handleFKCase`
- **Date:** 2026-09-02
