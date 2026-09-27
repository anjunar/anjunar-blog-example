-- Optional local example data for chapter 8, not a schema migration.
-- Apply after SchemaMain migrate. Re-running keeps these example rows unchanged.
BEGIN;

INSERT INTO public.blog_post
  (id, version, slug, title, content, summary, status, published_at)
VALUES
  ('b62db12a-61a7-46a5-b8d2-c487f80e825a', 0,
   'our-first-public-post', 'Our first public post',
   'This is the complete article. The list response leaves this text out.',
   'A working REST response from PostgreSQL.',
   'PUBLISHED', TIMESTAMPTZ '2026-09-27 10:15:42.123456+00'),
  ('9f524fe5-649b-4d91-aeb6-77e6dc034cf1', 0,
   'our-private-draft', 'Our private draft',
   'This draft must not appear in the public API.',
   NULL, 'DRAFT', NULL)
ON CONFLICT (id) DO NOTHING;

COMMIT;
