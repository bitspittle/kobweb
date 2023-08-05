---
root: .components.layouts.PageLayout("MARKDOWN")
---

## Markdown Example

This is [an example][id] reference-style link.

This site is generated from markdown.

Create rich, dynamic web apps with ease, leveraging [Kotlin](https://kotlinlang.org/) and [Compose HTML](https://github.com/JetBrains/compose-multiplatform#compose-html).

Markdown also supports

```
Multi-line
code blocks
```

and tables:

| Tables | Are    | Cool   |
|--------|--------|--------|
| cell 1 | cell 2 | cell 3 |

and `inline` code as well.

* And
* list
* items

---

You can use blockquotes:

> The trouble with quotes on the internet is you never know if they are genuine.
>
> -- Abraham Lincoln

You can use <span id="md-inline-demo">inlined html</span> tags. You can inspect this page to see that "inlined html" is
wrapped in a span.

You can also use block tags, like `<a>` and `<pre>`. Here, we use html blocks to create a Discord badge:

<a href="https://discord.gg/5NZ2GKV5Cs">
<img alt="Varabyte Discord" src="https://img.shields.io/discord/886036660767305799.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2" />
</a>

```
<a href="https://discord.gg/5NZ2GKV5Cs">
<img alt="Varabyte Discord" src="https://img.shields.io/discord/886036660767305799.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2" />
</a>
```

Alternately, you can use `{{{ code }}}` to call into Kotlin code, which itself can make Compose HTML calls. In fact,
the following link is actually provided by Kotlin code:

{{{ GoHomeLink }}}

[id]: http://example.com/
