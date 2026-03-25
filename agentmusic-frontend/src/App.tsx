function App() {
  return (
    <main className="app-shell">
      <section className="hero-card">
        <p className="eyebrow">AgentMusic Frontend</p>
        <h1>Modern React migration baseline is ready.</h1>
        <p className="description">
          This app is the new frontend workspace for AgentMusic. The cloned
          Spotify reference UI stays in <code>agentmusic-frontend-reference</code>
          and will be migrated here module by module.
        </p>
        <div className="status-grid">
          <article className="status-item">
            <h2>Reference Source</h2>
            <p>spotify-web-player clone preserved for selective migration.</p>
          </article>
          <article className="status-item">
            <h2>Target Stack</h2>
            <p>Vite + React + TypeScript, aligned for future backend API work.</p>
          </article>
          <article className="status-item">
            <h2>Next Step</h2>
            <p>Move layout, assets, and page shells into typed React modules.</p>
          </article>
        </div>
      </section>
    </main>
  )
}

export default App
