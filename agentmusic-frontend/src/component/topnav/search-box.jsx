import * as Icons from '../icons'
import styles from './search-box.module.css'

function SearchBox() {
  return (
    <div className={styles.SeachBox}>
      <Icons.Search />
      <input placeholder="搜索歌手、歌曲或播客" maxLength="80" />
    </div>
  )
}

export default SearchBox
