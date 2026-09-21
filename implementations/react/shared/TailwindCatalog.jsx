import React from "react";

export function TailwindCatalog({ data }) {

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      {/* Header & Search */}
      <div className="flex flex-col md:flex-row justify-between items-center mb-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">{data.title}</h1>
        <form action="/search" className="mt-4 md:mt-0 flex w-full md:w-auto">
          <input
            type="search"
            name="q"
            placeholder="Search projects..."
            className="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
          />
          <button
            type="submit"
            className="ml-3 inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
          >
            Search
          </button>
        </form>
      </div>

      {/* Filters */}
      <div className="flex space-x-4 mb-8 overflow-x-auto pb-2">
        {data.platforms.map((platform) => (
          <a
            key={platform.id}
            href={`?platform=${platform.id}`}
            className={`px-3 py-2 font-medium text-sm rounded-md ${
              platform.active
                ? "bg-indigo-100 text-indigo-700"
                : "text-slate-500 hover:text-slate-700 bg-slate-100 hover:bg-slate-200"
            }`}
          >
            {platform.name}
          </a>
        ))}
      </div>

      {/* Project Grid */}
      {data.sections.map((section, sIdx) => (
        <div key={sIdx} className="mb-12">
          <h2 className="text-2xl font-bold text-slate-900 mb-6">{section.title}</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {section.projects.map((project, pIdx) => (
              <div
                key={pIdx}
                className="bg-white overflow-hidden shadow rounded-lg border border-slate-200 flex flex-col"
              >
                <div className="px-4 py-5 sm:p-6 flex-grow">
                  <div className="flex items-center justify-between mb-4">
                    <span className="text-sm font-medium text-indigo-600">{project.author}</span>
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                      ★ {project.starCount}
                    </span>
                  </div>
                  <h3 className="text-lg font-medium text-slate-900 mb-2">{project.name}</h3>
                  <p className="text-sm text-slate-500 line-clamp-3">{project.description}</p>
                </div>
                <div className="bg-slate-50 px-4 py-4 sm:px-6 flex flex-wrap gap-2">
                  {project.badges.map((badge, bIdx) => (
                    <span
                      key={bIdx}
                      className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800 border border-slate-200"
                    >
                      {badge}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}

      {/* Footer */}
      <footer className="mt-12 border-t border-slate-200 pt-8 text-center pb-12">
        <p className="text-sm text-slate-500">© 2024 Compose HTML Benchmarks.</p>
      </footer>
    </div>
  );
}
