import { NavLink, useHistory, useLocation } from 'react-router-dom'
import styles from './topnav.module.css'

const ROUTE_META = [
  {
    match: (pathname) => pathname === '/',
    eyebrow: 'Conversation',
    title: 'Agent Chat',
    detail: 'Recommendations and playback control in one thread.',
  },
  {
    match: (pathname) => pathname.startsWith('/music'),
    eyebrow: 'Curated view',
    title: 'Music Home',
    detail: 'Playlists, tracks, artists, and albums surfaced by the agent.',
  },
  {
    match: (pathname) => pathname.startsWith('/playlist/'),
    eyebrow: 'Playlist detail',
    title: 'Listening context',
    detail: 'Inspect a generated playlist and jump into playback.',
  },
]

function Topnav() {
  const history = useHistory()
  const location = useLocation()
  const meta = ROUTE_META.find((item) => item.match(location.pathname)) ?? ROUTE_META[0]

  return (
    <nav className={styles.Topnav}>
      <div className={styles.Bar}>
        <div className={styles.HistoryRail}>
          <button className={styles.HistoryButton} type="button" onClick={() => history.goBack()}>
            <span aria-hidden="true">&larr;</span>
          </button>
          <button className={styles.HistoryButton} type="button" onClick={() => history.goForward()}>
            <span aria-hidden="true">&rarr;</span>
          </button>
        </div>

        <div className={styles.RouteCopy}>
          <p className={styles.Eyebrow}>{meta.eyebrow}</p>
          <div className={styles.RouteRow}>
            <h2>{meta.title}</h2>
            <span className={styles.Detail}>{meta.detail}</span>
          </div>
        </div>

        <div className={styles.NavCluster}>
          <div className={styles.RouteSwitch}>
            <NavLink exact to="/" activeClassName={styles.ActiveRoute}>
              Agent Chat
            </NavLink>
            <NavLink to="/music" activeClassName={styles.ActiveRoute}>
              Music
            </NavLink>
          </div>
          <div className={styles.ProfileBadge}>Demo Session</div>
        </div>
      </div>
    </nav>
  )
}

export default Topnav
