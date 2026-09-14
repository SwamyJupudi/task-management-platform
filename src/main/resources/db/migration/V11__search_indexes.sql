-- V11: trigram indexes for the free-text searches that already exist.
--
-- Nothing about the application changes. Every search below was already
-- written as `lower(column) LIKE '%term%'` and already returned the right
-- rows; each one was a sequential scan of the table, because a leading
-- wildcard makes a B-tree index useless. These indexes make the same queries
-- fast without a line of Java moving, which is the whole point of doing it
-- this way: a query change and an index change in one migration would leave
-- nobody able to say which of the two mattered.
--
-- WHY TRIGRAM AND NOT FULL TEXT SEARCH
--
-- A tsvector index answers "which rows contain this word", after stemming and
-- stop-word removal. That is a different question from the one these searches
-- ask, which is "which rows contain this substring": somebody typing `auth`
-- into a task filter expects to find "Build authentication", and a word-based
-- index would not return it. Trigram indexing matches substrings, so it
-- accelerates the query that is actually there rather than requiring the
-- query to be rewritten into one it suits. `architecture.md` recorded this as
-- "the trigram or GIN index"; it is the trigram one, for that reason.
--
-- WHAT THIS DOES NOT HELP
--
-- A search term shorter than three characters contains no complete trigram,
-- so PostgreSQL cannot use these indexes for it and falls back to the scan it
-- was doing before. That is a property of trigram indexing rather than a
-- defect here, and it is the reason no query was changed to depend on an
-- index being used.
--
-- EXPRESSION INDEXES MUST MATCH THE QUERY EXACTLY
--
-- Each index is on `lower(column)`, spelled the same way the query spells it.
-- An index on the bare column would be ignored, because the planner matches
-- expressions, not intentions. `users.email` is `citext`, which is binary
-- coercible to `text` and has no `lower(citext)` of its own, so `lower(email)`
-- resolves to `lower(text)` on both sides and the two agree.
--
-- The partial clauses match too. Every one of these searches filters
-- `deleted_at IS NULL` -- TaskQuery, ProjectQuery and all four UserRepository
-- variants do it unconditionally -- so a partial index is both usable and
-- smaller than a full one. If a search is ever added that looks at deleted
-- rows, it will need its own index rather than this one.
--
-- LOCKING
--
-- These are plain CREATE INDEX statements, which take a lock that blocks
-- writes to the table while the index is built. That is the right trade at
-- this size and it keeps the migration inside Flyway's transaction, so a
-- failure half way through leaves no half-built index behind. On a table
-- large enough for the build to take minutes rather than seconds, the answer
-- is CREATE INDEX CONCURRENTLY run outside a migration, which cannot be
-- transactional and therefore cannot be this file.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- tasks.title
--
-- The workspace task search: My Tasks, the calendar and the global search box
-- all reach TaskQuery, which matches the title with a leading wildcard. This
-- is the busiest of the six.
-- ---------------------------------------------------------------------------
CREATE INDEX tasks_title_trgm_idx
    ON tasks USING gin (lower(title) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- projects.name and projects.key
--
-- ProjectQuery matches both with one pattern, so the planner needs an index
-- on each and combines them with a bitmap OR. One index over both columns
-- would not work: they are separate predicates, not a concatenation.
-- ---------------------------------------------------------------------------
CREATE INDEX projects_name_trgm_idx
    ON projects USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX projects_key_trgm_idx
    ON projects USING gin (lower(key) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- users.email, users.first_name, users.last_name
--
-- The account directory and the admin user search share one query shape that
-- ORs all three columns, so all three need an index for the bitmap OR to
-- avoid falling back to a scan. This is the search with the most rows behind
-- it in an installation of any size, and the admin variant is one of the few
-- queries in the platform with no workspace predicate to narrow it first.
-- ---------------------------------------------------------------------------
CREATE INDEX users_email_trgm_idx
    ON users USING gin (lower(email) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX users_first_name_trgm_idx
    ON users USING gin (lower(first_name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX users_last_name_trgm_idx
    ON users USING gin (lower(last_name) gin_trgm_ops)
    WHERE deleted_at IS NULL;
