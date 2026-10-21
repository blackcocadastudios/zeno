"use strict";

/*
 * Zeno's reader mode. The page to read comes in this page's address
 * (reader.html?url=…): it is downloaded again, its article is found by
 * Mozilla's Readability (the code behind Firefox's reader view) and shown
 * with Zeno's typography. Nothing from the page runs here: its HTML is parsed
 * into an inert document and cleaned before it is shown, and this page's
 * policy allows no scripts but its own.
 */

const TEXT = {
  failed: "Não deu para montar a leitura desta página.",
  failedHint: "Algumas páginas só mostram o texto depois de rodar scripts, e outras não são artigos.",
  original: "Ver a página original",
  minutes: (n) => (n <= 1 ? "1 min de leitura" : `${n} min de leitura`),
};

/** Characters a calm reader goes through in a minute. */
const CHARS_PER_MINUTE = 1000;

/** Never shown: they run code, load other pages or ask for input. */
const REMOVED = new Set([
  "script", "style", "link", "meta", "base", "iframe", "frame", "frameset", "object", "embed",
  "applet", "form", "input", "button", "select", "textarea", "noscript", "template", "dialog",
]);

/** Attributes holding addresses: only web (and mail, for links) addresses stay. */
const URL_ATTRIBUTES = new Set(["href", "src", "xlink:href", "poster", "action", "formaction", "cite", "background", "longdesc"]);

const main = document.getElementById("reader");
const pageUrl = new URLSearchParams(location.search).get("url");

read().catch(() => fail());

async function read() {
  if (!pageUrl || !/^https?:\/\//i.test(pageUrl)) return fail();
  const response = await fetch(pageUrl, { credentials: "include" });
  const type = response.headers.get("content-type") || "";
  if (!response.ok || !/html/i.test(type)) return fail();
  const url = response.url || pageUrl;
  const doc = new DOMParser().parseFromString(decode(await response.arrayBuffer(), type), "text/html");

  // Relative links and images in the article belong to the page's own site.
  const declared = doc.querySelector("base[href]");
  const base = doc.createElement("base");
  base.href = declared ? new URL(declared.getAttribute("href"), url).href : url;
  declared?.remove();
  doc.head.prepend(base);

  const article = new Readability(doc, { serializer: (el) => el }).parse();
  if (!article || !article.content || !article.textContent?.trim()) return fail();
  show(article, url);
}

/** The page's text in the charset it declares (header, then <meta>), UTF-8 otherwise. */
function decode(buffer, contentType) {
  let charset = /charset\s*=\s*["']?([\w:-]+)/i.exec(contentType)?.[1];
  if (!charset) {
    const head = new TextDecoder("windows-1252").decode(buffer.slice(0, 4096));
    charset = /<meta[^>]+charset\s*=\s*["']?([\w:-]+)/i.exec(head)?.[1];
  }
  try {
    return new TextDecoder(charset || "utf-8").decode(buffer);
  } catch {
    return new TextDecoder("utf-8").decode(buffer);
  }
}

function show(article, url) {
  const site = article.siteName || hostOf(url);
  document.title = article.title || site;
  if (article.lang) document.documentElement.lang = article.lang;
  if (article.dir) document.documentElement.dir = article.dir;

  const header = element("header");
  header.append(element("p", "site", site));
  if (article.title) header.append(element("h1", null, article.title));
  const minutes = Math.max(1, Math.round(article.length / CHARS_PER_MINUTE));
  header.append(element("p", "details", [article.byline, TEXT.minutes(minutes)].filter(Boolean).join(" · ")));

  // Cleaned while still in the inert document: nothing loads before that.
  clean(article.content);
  const body = element("article");
  body.append(document.adoptNode(article.content));

  const footer = element("footer");
  footer.append(link(url, TEXT.original));

  main.replaceChildren(header, body, footer);
  main.removeAttribute("aria-busy");
}

function clean(root) {
  for (const node of [...root.querySelectorAll("*")]) {
    // Already gone with a removed ancestor.
    if (!root.contains(node)) continue;
    const tag = node.localName;
    if (REMOVED.has(tag)) {
      node.remove();
      continue;
    }
    for (const { name } of [...node.attributes]) {
      const lower = name.toLowerCase();
      if (lower.startsWith("on") || lower === "style" || lower === "target" || lower === "srcdoc" || lower === "ping") {
        node.removeAttribute(name);
      } else if (URL_ATTRIBUTES.has(lower) && !allowed(node.getAttribute(name), tag === "a" && lower === "href", lower === "src")) {
        node.removeAttribute(name);
      }
    }
    if (tag === "a") node.setAttribute("rel", "noreferrer");
    if (tag === "img") {
      // One-pixel images are trackers, not illustrations.
      const width = Number(node.getAttribute("width"));
      const height = Number(node.getAttribute("height"));
      if ((width > 0 && width <= 2) || (height > 0 && height <= 2)) {
        node.remove();
        continue;
      }
      node.setAttribute("loading", "lazy");
      node.setAttribute("referrerpolicy", "no-referrer");
    }
  }
}

function allowed(value, isLink, isSource) {
  if (isSource && /^data:image\//i.test(value)) return true;
  try {
    const { protocol } = new URL(value);
    return protocol === "https:" || protocol === "http:" || (isLink && protocol === "mailto:");
  } catch {
    return false;
  }
}

function fail() {
  const box = element("div", "failed");
  box.append(element("p", null, TEXT.failed), element("p", "hint", TEXT.failedHint));
  if (pageUrl && /^https?:\/\//i.test(pageUrl)) box.append(link(pageUrl, TEXT.original));
  main.replaceChildren(box);
  main.removeAttribute("aria-busy");
}

function link(url, text) {
  const a = element("a", null, text);
  a.href = url;
  a.rel = "noreferrer";
  return a;
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}
