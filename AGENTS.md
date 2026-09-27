# Working on the companion project

- Check Git status before making changes and preserve existing work.
- This project is a step-by-step tutorial for building a blog without multitenancy.
- Anjunar Stack is a technical reference, not a local build dependency.
- Implement only the step currently being discussed. Do not copy the entire stack in advance.
- Use libraries from Maven Central; do not introduce ProjectRef or publishLocal dependencies.
- Examples must run against the corresponding project revision.
- Import types and APIs instead of using fully qualified names in code. Import java.lang and use lang.Long, lang.Integer, etc. for Java wrappers. Use import aliases for name collisions.
- Use plain JPA and Bean Validation annotations on fields declared in the class body. An explicit @field target is unnecessary there; use it when an annotation on a constructor parameter needs to target the backing field.
- Write the blog articles, documentation, roadmap, code comments, and example text in English.
- Name article slugs abt-NN-title-in-kebab-case, where NN is the two-digit article number (for example, abt-01-building-a-complete-web-application-with-scala).
- English is the application's primary language. Introduce German as the second language in the internationalization chapter.
- Preserve the contract across the entity, EntitySchema, REST graph, and frontend model.
- Discover entity classes through EntityExtension and EntityRegistry. Entity-containing archives need beans.xml with bean-discovery-mode="all"; do not maintain a manual entity list in Persistence.
- Keep each UI tree together in compose; use the i18n macro for new translatable UI messages.
- Add appropriate functional checks alongside each feature.
- Current check: sbt --server "application-backend/testFull". Use a separate local PostgreSQL database, BLOG_DB_PASSWORD, and the chapter 5 SQL schema; see README.md for setup. Model tests remove their own rows; transaction tests create and drop a uniquely named probe table.
- Do not modify the reference repository or content repository without a corresponding request.
