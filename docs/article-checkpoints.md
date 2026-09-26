# Article checkpoints

Each implementation article refers to an immutable source revision. Use the
checkpoint for the article you are reading so that later changes on main do not
alter its examples.

## 02 — From an Empty Directory to a Runnable Project

- Article slug: `abt-02-from-an-empty-directory-to-a-runnable-project`
- Source revision: [0b9ea6f](https://github.com/anjunar/anjunar-blog-example/tree/0b9ea6f9447069bbe291e486fcdc8fb633d10656)
- Source changes: [PR #1](https://github.com/anjunar/anjunar-blog-example/pull/1)

This chapter recreates the initial backend from an empty directory: the sbt
build, Undertow, RESTEasy, Weld/CDI, the greeting endpoint, and its HTTP
integration test.

### Check out this version

Run the following in a directory where `anjunar-blog-example` does not yet exist:

```text
git clone https://github.com/anjunar/anjunar-blog-example.git
cd anjunar-blog-example
git switch --detach 0b9ea6f9447069bbe291e486fcdc8fb633d10656
sbt --server "application-backend/testFull"
```

Use JDK 25 and sbt; the project selects sbt 2.0.9 and Scala 3.9.0. The test should
report one successful test. It exercises the real HTTP endpoint, CDI injection,
and the 404 response for an unknown resource. `testFull` runs the tests even when
sbt 2's incremental `test` task would skip previously successful tests.

### Start and check the server

```text
sbt --server "application-backend/run"
```

In another terminal:

```text
curl -i http://127.0.0.1:8080/service/hello
```

Use `curl.exe` in Windows PowerShell if necessary. Expect HTTP 200, a
`text/plain` content type, and `Welcome to Anjunar Blog Tutorial!`.

Set the `BLOG_PORT` environment variable if 8080 is occupied, and use the
selected port in the request URL. Stop the server with Ctrl+C.

The checkout is detached to keep the article reproducible. To continue your own
implementation from this point, create a branch with `git switch -c my-blog`.
