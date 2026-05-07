import { Link } from 'react-router-dom'
import styles from './Logo.module.css'

function Logo() {
  return (
    <Link className={styles.link} to="/">
      <span className={styles.wordmark}>AgentMusic</span>
    </Link>
  )
}

export default Logo
