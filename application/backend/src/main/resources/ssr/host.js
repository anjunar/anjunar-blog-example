// This is the small host contract used by HttpJson and Scala.js timers, not a browser DOM.
globalThis.Headers = class {
  constructor() { this.values = Object.create(null); }
  set(name, value) { this.values[name.toLowerCase()] = String(value); }
  get(name) { return this.values[name.toLowerCase()] ?? null; }
};
globalThis.fetch = (path, options = {}) => {
  try {
    const response = fetchPublic(String(path), String(options.method ?? "GET"));
    const headers = new Headers();
    headers.set("Content-Type", response.contentType);
    return Promise.resolve({
      status: response.status,
      ok: response.status >= 200 && response.status < 300,
      headers,
      text: () => Promise.resolve(response.body)
    });
  } catch (error) {
    return Promise.reject(error);
  }
};
globalThis.setTimeout = (callback, delay = 0, ...args) =>
  scheduleTimer(() => callback(...args), Number(delay));
globalThis.clearTimeout = id => cancelTimer(Number(id));
