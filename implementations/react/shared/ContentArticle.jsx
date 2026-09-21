import React from "react";

export function ContentArticle({ data }) {

  return (
    <>
      {/* Breadcrumbs */}
      <nav aria-label="Breadcrumb">
        <ol>
          {data.breadcrumbs.map((crumb, idx) => (
            <li key={idx}>
              <a href={crumb.url} aria-current={crumb.isCurrent ? "page" : undefined}>
                {crumb.label}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <main>
        <article>
          {/* Header */}
          <header>
            <h1>{data.title}</h1>
            <div className="meta">
              <span>By {data.author}</span>
              <time dateTime={data.publishDateIso}>{data.publishDateFormatted}</time>
              <span>{data.readingTimeMin} min read</span>
            </div>
            <img
              src={data.heroImage.url}
              alt={data.heroImage.alt}
              width={data.heroImage.width}
              height={data.heroImage.height}
              loading="lazy"
            />
          </header>

          {/* Table of Contents */}
          <nav aria-label="Table of contents">
            <h2>Table of Contents</h2>
            <ul>
              {data.toc.map((item, idx) => (
                <li key={idx}>
                  <a href={`#${item.targetId}`}>{item.title}</a>
                </li>
              ))}
            </ul>
          </nav>

          {/* Content Sections */}
          {data.sections.map((section) => (
            <section key={section.id} id={section.id}>
              <h2>{section.title}</h2>
              {section.blocks.map((block, bIdx) => (
                <React.Fragment key={bIdx}>
                  {block.type === "PARAGRAPH" && <p>{block.content}</p>}
                  {block.type === "CODE" && (
                    <pre>
                      <code data-language={block.language || "text"}>
                        {block.content}
                      </code>
                    </pre>
                  )}
                  {block.type === "IMAGE" && (
                    <img src={block.url || ""} alt={block.content} loading="lazy" />
                  )}
                  {block.type === "LINK" && (
                    <p>
                      <a href={block.url || ""} target="_blank" rel="noreferrer">
                        {block.content}
                      </a>
                    </p>
                  )}
                </React.Fragment>
              ))}
            </section>
          ))}

          {/* Footer / Tags */}
          <footer>
            <ul className="tags">
              {data.tags.map((tag, idx) => (
                <li key={idx}>
                  <a href={`/tags/${tag}`}>{tag}</a>
                </li>
              ))}
            </ul>
          </footer>
        </article>

        <aside aria-label="Related articles">
          <h3>Related Articles</h3>
          <ul>
            {data.relatedArticles.map((art, idx) => (
              <li key={idx}>
                <a href={art.url}>{art.title}</a>
              </li>
            ))}
          </ul>
        </aside>
      </main>

      {/* Site Footer */}
      <footer>
        <p>© 2024 Benchmarks</p>
      </footer>
    </>
  );
}
