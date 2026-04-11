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
  const [isNowPlayingOpen, setIsNowPlayingOpen] = useState(false)
  const [isQueueOpen, setIsQueueOpen] = useState(false)
  const showNowPlayingPanel = size.width > CONST.MOBILE_SIZE && isNowPlayingOpen

  const handleOpenNowPlayingPanel = () => {
    setIsNowPlayingOpen(true)
  }

  const handleToggleNowPlayingPanel = () => {
    setIsNowPlayingOpen((current) => {
      const next = !current
      if (!next) {
        setIsQueueOpen(false)
      }
      return next
    })
  }

  const handleToggleQueueDrawer = () => {
    setIsNowPlayingOpen(true)
    setIsQueueOpen((current) => !current)
  }

  const handleCloseNowPlayingPanel = () => {
    setIsNowPlayingOpen(false)
    setIsQueueOpen(false)
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
              isQueueOpen={isQueueOpen}
              onClose={handleCloseNowPlayingPanel}
              onToggleQueue={handleToggleQueueDrawer}
            />
          </div>
        ) : null}
        <Footer
          onOpenNowPlayingPanel={handleOpenNowPlayingPanel}
          onToggleNowPlayingPanel={handleToggleNowPlayingPanel}
          onToggleQueueDrawer={handleToggleQueueDrawer}
          isNowPlayingOpen={showNowPlayingPanel}
          isQueueOpen={showNowPlayingPanel && isQueueOpen}
        />
      </div>
    </Router>
  )
}

export default App
