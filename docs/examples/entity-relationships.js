// Sign in as the development administrator, then paste this into the browser console.
// Leaves one draft and one reusable tag in the tutorial database.
(async () => {
  const state = await fetch("/service/auth/session").then(response => response.json());
  const capability = (links, rel) => {
    const value = links?.find(link => link.rel === rel);
    if (!value) throw new Error("Missing capability: " + rel);
    return value;
  };
  async function send(link, body) {
    const target = new URL(link.url, location.origin);
    if (target.origin !== location.origin || !target.pathname.startsWith("/service/"))
      throw new Error("Unexpected API destination.");
    return fetch(target, {
      method: link.method, credentials: "same-origin",
      headers: { Accept: "application/json, application/problem+json",
        "Content-Type": "application/json", "X-CSRF-Token": state.csrfToken },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
  }
  async function json(link, body, status = 200) {
    const response = await send(link, body);
    if (response.status !== status) throw new Error("Unexpected HTTP status: " + response.status);
    return response.json();
  }
  const catalog = await json(capability(state.$links, "tags"));
  const createdTag = await json(capability(catalog.$links, "create"), {
    slug: "relationships-" + crypto.randomUUID(), name: "Entity relationships"
  }, 201);
  const posts = await json(capability(state.$links, "editorial"));
  const created = await json(capability(posts.$links, "create"), {
    slug: "references-" + crypto.randomUUID(), title: "References are not nested edits",
    content: "A post links to an existing author and reusable tags.",
    author: { id: state.account.id }, tags: [{ id: createdTag.data.id }]
  }, 201);
  const update = capability(created.$links, "update");
  const detailLink = { url: update.url, method: "GET" };

  // A nested tag edit must reject the WHOLE request, including the title.
  const rejected = await send(update, {
    version: created.data.version, title: "This title must roll back",
    tags: [{ id: createdTag.data.id, name: "An unauthorized nested rename" }]
  });
  if (rejected.status !== 400) throw new Error("Expected the nested edit to fail.");
  const unchanged = await json(detailLink);
  if (unchanged.data.title !== created.data.title || unchanged.data.version !== created.data.version)
    throw new Error("The rejected request changed the post.");

  // Omission keeps the references; explicit null and [] remove the links.
  const renamed = await json(update, {
    version: unchanged.data.version, title: "Shared entities, deliberate writes"
  });
  if (renamed.data.author.id !== state.account.id || renamed.data.tags[0].id !== createdTag.data.id)
    throw new Error("Omission must preserve existing references.");
  const cleared = await json(update, { version: renamed.data.version, author: null, tags: [] });
  if (cleared.data.author != null || (cleared.data.tags ?? []).length !== 0)
    throw new Error("The links were not cleared.");

  // Updating the independent tag proves that unlinking did not delete it.
  const retained = await json(capability(createdTag.$links, "update"), {
    version: createdTag.data.version, name: "A retained shared tag"
  });
  return {
    postId: created.data.id, tagId: retained.data.id, rejectedStatus: rejected.status,
    createdVersion: created.data.version, clearedVersion: cleared.data.version,
    tagVersion: retained.data.version, preview: "/en/editorial/posts/" + created.data.id
  };
})();
