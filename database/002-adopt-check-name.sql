-- Run once on the chapter 5 schema, before adopting it with the baseline checkpoint.
-- This generated name belongs to status SchemaId d4f39c20/cf271a06 and DRAFT/PUBLISHED.
-- Renaming preserves the constraint predicate and every row.
ALTER TABLE public.blog_post
    RENAME CONSTRAINT ck_blog_post_status TO ck_6576a412cbd2bfbd503a3350;
