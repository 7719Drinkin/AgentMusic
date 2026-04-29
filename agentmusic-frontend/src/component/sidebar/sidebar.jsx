import { useEffect, useState } from 'react'
import useMousePosition from '../../hooks/useMousePosition'
import { Logo } from '../icons'
import Navigation from './navigation'
import Playlist from './playlist'
import styles from './sidebar.module.css'

function Sidebar() {
  const [width, setWidth] = useState(236)
  const [isMouseDown, setIsMouseDown] = useState(false)
  const { x } = useMousePosition()

  useEffect(() => {
    if (!isMouseDown) {
      return
    }

    const handleMove = () => {
      if (x > 200 && x < 316) {
        setWidth(x)
      }
    }

    const handleUp = () => {
      setIsMouseDown(false)
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleUp)

    return () => {
      document.removeEventListener('mousemove', handleMove)
      document.removeEventListener('mouseup', handleUp)
    }
  }, [isMouseDown, x])

  return (
    <nav className={styles.SideNavbar} style={{ width: `${width}px` }}>
      <div className={styles.Fixed}>
        <div className={styles.SidebarHeader}>
          <div>
            <Logo />
          </div>
          <div>
            <Navigation />
          </div>
        </div>
        <div className={styles.SidebarScroll} data-testid="sidebar-playlist-scroll">
          <Playlist />
        </div>
      </div>
      <div
        className={`${styles.changeWidth} ${isMouseDown ? styles.ActiveChange : ''}`}
        onMouseDown={() => {
          setIsMouseDown(true)
        }}
      ></div>
    </nav>
  )
}

export default Sidebar
