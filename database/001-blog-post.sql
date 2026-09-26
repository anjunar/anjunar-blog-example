CREATE TABLE public.blog_post (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    slug varchar(220) NOT NULL,
    title varchar(180) NOT NULL,
    content text NOT NULL,
    status varchar(24) NOT NULL,
    published_at timestamp(6) with time zone,
    CONSTRAINT uq_blog_post_slug UNIQUE (slug),
    CONSTRAINT ck_blog_post_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_blog_post_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
    )
);
