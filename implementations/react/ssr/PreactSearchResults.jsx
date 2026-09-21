import React from "react";

function SearchFooter({ sections }) {
  return (
    <footer id="glbfooter" role="contentinfo" className="gh-w">
      <div>
        <div id="rtm_html_1650">
          <div id="rtm_html_1651" />
          <h2 className="gh-ar-hdn">Additional site navigation</h2>
          <div id="gf-BIG" className="gffoot">
            <table className="gf-t"><tbody><tr>
              {sections.map((section) => (
                <td key={section.title}><ul>
                  <li className="gf-li"><h3 className="gf-bttl">
                    {section.headingHref
                      ? <a href={section.headingHref} className="gf-bttl thrd">{section.title}</a>
                      : section.title}
                  </h3></li>
                  {section.links.map((link) => (
                    <li className="gf-li" key={`${link.label}:${link.href}`}>
                      <a href={link.href} className="thrd">{link.label}</a>
                    </li>
                  ))}
                </ul></td>
              ))}
            </tr></tbody></table>
          </div>
        </div>
      </div>
    </footer>
  );
}

export function PreactSearchResults({ data }) {
  return (
    <div className="search-results">
      <div>
        {data.items.map((item) => (
          <div className="search-results-item" key={item.id}>
            <h2>{item.title}</h2>
            <div className="lvpic pic img left"><div className="lvpicinner full-width picW">
              <a href={`/buy/${item.id}`} className="img imgWr2"><img src={item.image} alt={item.title} loading="lazy" /></a>
            </div></div>
            <span className="price">{item.price}</span>
            <button className="buy-now" type="button">Buy now!</button>
          </div>
        ))}
      </div>
      <SearchFooter sections={data.footerSections} />
    </div>
  );
}
