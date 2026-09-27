// Sign in as the development administrator, then paste this into the browser console.
// It leaves one new draft that you can inspect in the editorial workspace.
(async () => {
  const state = await fetch("/service/auth/session").then(response => response.json());
  const entry = state.$links?.find(link => link.rel === "editorial");
  if (!entry) throw new Error("Sign in as an administrator first.");

  async function send(link, body) {
    const target = new URL(link.url, location.origin);
    if (target.origin !== location.origin || !target.pathname.startsWith("/service/"))
      throw new Error("Unexpected API destination.");
    return fetch(target, {
      method: link.method,
      credentials: "same-origin",
      headers: {
        Accept: "application/json, application/problem+json",
        "Content-Type": "application/json",
        "X-CSRF-Token": state.csrfToken
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
  }

  const tableResponse = await send(entry);
  if (!tableResponse.ok) throw new Error("Could not open the editorial collection.");
  const table = await tableResponse.json();
  const create = table.$links.find(link => link.rel === "create");
  const response = await send(create, {
    slug: "prepared-" + crypto.randomUUID(),
    title: "A prepared change",
    content: "The controller checks access before applying these values."
  });
  if (response.status !== 201) throw new Error("The draft was not created.");
  const created = await response.json();
  const update = created.$links.find(link => link.rel === "update");
  const savedResponse = await send(update, {
    version: created.data.version,
    title: "A safely updated post",
    summary: "Prepared, authorized, applied and validated."
  });
  if (!savedResponse.ok) throw new Error("The draft was not updated.");
  const saved = await savedResponse.json();

  const staleResponse = await send(update, {
    version: created.data.version,
    title: "This old edit must not win"
  });
  if (staleResponse.status !== 409) throw new Error("Expected a version conflict.");
  const conflict = await staleResponse.json();
  return {
    id: saved.data.id,
    createdVersion: created.data.version,
    savedVersion: saved.data.version,
    conflictStatus: staleResponse.status,
    conflict: conflict.detail,
    preview: "/en/editorial/posts/" + saved.data.id
  };
})();
