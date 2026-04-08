import React, { useState } from 'react'
import { BrowserRouter as Router, Switch, Route } from 'react-router-dom'
import useWindowSize from './hooks/useWindowSize'
import Sidebar from './component/sidebar/sidebar'
import MobileNavigation from './component/sidebar/mobile-navigation'
import Footer from './component/footer/footer'
import CurrentPlayingPanel from './component/current-playing/current-playing-panel'
import ChatPage from './pages/chat'
import Home from './pages/home'
import Search from './pages/search'
import Library from './pages/library'
import PlaylistPage from './pages/playlist'
import CONST from './constants/index'
import styles from './style/App.module.css'

function App() {
  const size = useWindowSize()
  const [nowPlayingPanel, setNowPlayingPanel] = useState({
    open: false,
    view: 'details',
  })
  const showNowPlayingPanel = size.width > CONST.MOBILE_SIZE && nowPlayingPanel.open

  const handleOpenNowPlayingPanel = (view = 'details') => {
    setNowPlayingPanel({ open: true, view })
  }

  const handleToggleNowPlayingPanel = (view = 'details') => {
    setNowPlayingPanel((current) => {
      if (current.open && current.view === view) {
        return { ...current, open: false }
      }

      return { open: true, view }
    })
  }

  return (
    <Router>
      <div className={`${styles.layout} ${showNowPlayingPanel ? styles.layoutWithPanel : ''}`}>
        {size.width > CONST.MOBILE_SIZE ? <Sidebar /> : <MobileNavigation />}
        <main className={styles.mainArea}>
          <Switch>
            <Route exact path="/">
              <ChatPage />
            </Route>
            <Route exact path="/music">
              <Home />
            </Route>
            <Route path="/search">
              <Search />
            </Route>
            <Route path="/library">
              <Library />
            </Route>
            <Route exact path="/playlist/:path">
              <PlaylistPage />
            </Route>
          </Switch>
        </main>
        {showNowPlayingPanel ? (
          <div className={styles.panelArea}>
            <CurrentPlayingPanel
              view={nowPlayingPanel.view}
              onClose={() => setNowPlayingPanel((current) => ({ ...current, open: false }))}
              onSwitchView={(view) => setNowPlayingPanel({ open: true, view })}
            />
          </div>
        ) : null}
        <Footer
          onOpenNowPlayingPanel={handleOpenNowPlayingPanel}
          onToggleNowPlayingPanel={handleToggleNowPlayingPanel}
          isNowPlayingOpen={showNowPlayingPanel}
          currentPanelView={nowPlayingPanel.view}
        />
      </div>
    </Router>
  )
}

export default App
