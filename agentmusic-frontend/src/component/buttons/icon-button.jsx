import { useState } from 'react'
import styles from './icon-button.module.css'

function IconButton({
  icon,
  activeicon,
  onClick,
  tooltip,
  disabled = false,
  className = '',
  active,
  toggleOnClick = true,
  ariaLabel,
  type = 'button',
}) {
  const [internalActive, setInternalActive] = useState(false)
  const isActive = typeof active === 'boolean' ? active : internalActive

  const handleClick = () => {
    if (disabled) {
      return
    }

    if (toggleOnClick && activeicon) {
      setInternalActive((current) => !current)
    }

    onClick?.()
  }

  return (
    <button
      className={`${styles.iconButton} ${isActive ? styles.activeButton : ''} ${className}`.trim()}
      onClick={handleClick}
      disabled={disabled}
      aria-label={ariaLabel || tooltip}
      type={type}
    >
      {isActive && activeicon ? activeicon : icon}
      {tooltip ? <span className={styles.tooltip}>{tooltip}</span> : null}
    </button>
  )
}

export default IconButton
