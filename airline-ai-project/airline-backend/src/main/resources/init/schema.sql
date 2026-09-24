-- pgvector is an extension, not part of stock Postgres, so it has to be enabled before
-- LangChain4j can create its table. The pgvector/pgvector image ships the binary; this
-- line is what switches it on for our database.
CREATE EXTENSION IF NOT EXISTS vector;
