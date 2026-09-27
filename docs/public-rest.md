# Public post API

Chapter 8 exposes two read operations:

| Request | Result |
| --- | --- |
| `GET /service/blog/posts?offset=0&limit=20` | A `Table[Data[BlogPost]]` containing published posts, without content. |
| `GET /service/blog/posts/{slug}` | A `Data[BlogPost]` containing the full published post. |

Draft and unknown slugs both return 404. These resources have no POST, PUT, or
DELETE methods; authentication and editing follow in later chapters.

## Response contract

A detail response has `data` and `schema`. The data is the JPA entity serialized
by the JSON mapper. The schema contains `entries` with a field's `name` and
`type`; it describes the selected fields, not the caller's permissions.

A list has `rows`, each with the same data/schema wrapper, and `size`, the total
number of published posts before pagination. Defaults are offset 0 and limit 20.
Offset must be nonnegative and limit must be 1–100. Invalid integers or bounds
return 400. Rows sort by publication time descending, then UUID ascending for
ties. Count and page are separate queries; concurrent publication can change the
total between them. This is basic offset pagination, not a snapshot guarantee.

The `BlogPost.list` named graph selects id, version, slug, title, summary, status,
and publishedAt. `BlogPost.detail` also includes content. Both queries explicitly
filter PUBLISHED. A graph selects fields; it does not grant access to rows.

The query supplies the graph as a JPA fetch hint. The REST writer passes it to
the mapper as an output projection. A fetch graph does not promise that Hibernate
omits every unselected basic column from SQL.

Mapper 1.1.5 adds `@type` markers and omits nulls and empty collections. An empty
list therefore returns `{"size":0,"@type":"Table"}`, with no `rows` member.
An offset beyond the last row also omits rows but retains the total size.
An absent optional summary is omitted from data while its schema entry remains.

## Try it locally

Start and migrate a separate local database using the README instructions.
Before inserting any examples, an empty database returns size 0.

The optional example script adds one published post and one draft. With Compose:

```text
docker compose cp database/examples/public-posts.sql postgres:/tmp/public-posts.sql
docker compose exec -T postgres psql -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --file /tmp/public-posts.sql
```

With native PostgreSQL, supply your local connection details:

```text
psql -h 127.0.0.1 -p 5433 -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --file database/examples/public-posts.sql
```

psql prompts for the database password; it does not read BLOG_DB_PASSWORD.
The script keeps its two fixed IDs unchanged when repeated. It is example data,
not an automatic startup action or a schema migration.

Start the server:

```text
sbt --server "application-backend/run"
```

In another terminal:

```text
curl -i "http://127.0.0.1:8080/service/blog/posts?offset=0&limit=20"
curl -i http://127.0.0.1:8080/service/blog/posts/our-first-public-post
curl -i http://127.0.0.1:8080/service/blog/posts/our-private-draft
curl -i "http://127.0.0.1:8080/service/blog/posts?limit=0"
curl -I http://127.0.0.1:8080/service/blog/posts/our-first-public-post
```

Use curl.exe in Windows PowerShell if needed. Expect 200, 200, 404, 400, and 200.
The first response includes the public post without content. The second includes
its complete content and version 0. HEAD has no response body.

## Request lifetime and validation

CDI discovers the new resource and JSON writer through RestComponentsExtension.
The writer retains the resource method's generic return type so the mapper can
resolve Table, Data, and BlogPost through both wrappers.

BlogPost implements EntityProvider, which enables graph selection in the mapper.
Its version now uses Scala Long initialized to -1, matching that interface.
Hibernate assigns 0 on insertion. The PostgreSQL bigint column and its SchemaId
are unchanged; an existing chapter 6/7 database reports AlreadyApplied with
zero SQL statements.

RESTEasy invokes the writer for implicit HEAD requests before suppressing the
body. TransactionBoundary keeps the EntityManager open through that writer,
just as it does for GET. Only responses without an entity finish in the response
filter. GET and HEAD still roll back; the writer's buffered response is sent
after transaction completion.

`sbt --server "application-backend/testFull"` runs 41 tests, including eight
HTTP checks for the public contract. The tests start the real server, seed their
own rows, and remove only those rows. Use a separate migrated PostgreSQL test
database. Error cases intentionally produce server logs.
