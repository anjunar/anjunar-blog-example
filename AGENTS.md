# Working on the companion project

- Check Git status before making changes and preserve existing work.
- This project is a step-by-step tutorial for building a blog without multitenancy.
- Anjunar Stack is a technical reference, not a local build dependency.
- Implement only the step currently being discussed. Do not copy the entire stack in advance.
- Use libraries from Maven Central; do not introduce ProjectRef or publishLocal dependencies.
- Examples must run against the corresponding project revision.
- Write the blog articles, documentation, roadmap, code comments, and example text in English.
- Name article slugs abt-NN-title-in-kebab-case, where NN is the two-digit article number (for example, abt-01-building-a-complete-web-application-with-scala).
- English is the application's primary language. Introduce German as the second language in the internationalization chapter.
- Preserve the contract across the entity, EntitySchema, REST graph, and frontend model.
- Keep each UI tree together in compose; use the i18n macro for new translatable UI messages.
- Add appropriate functional checks alongside each feature.
- Current check: sbt --server "application-backend/test".
- Do not modify the reference repository or content repository without a corresponding request.
