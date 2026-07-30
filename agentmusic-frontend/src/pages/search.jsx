import { useLocation } from 'react-router-dom'
import Topnav from '../component/topnav/topnav'
import TitleM from '../component/text/title-m'
import TextRegularM from '../component/text/text-regular-m'
import styles from './search.module.css'

function Search() {
  const location = useLocation()
  const params = new URLSearchParams(location.search)
  const artistQuery = params.get('artist')
  const artistName = artistQuery || location.state?.artistName || ''
  const artistImage = location.state?.artistImage || '/image/Playlist/liked-songs.PNG'

  return (
    <div className={styles.SearchPage}>
      <Topnav search={true} />

      <div className={styles.Search}>
        {artistName ? (
          <section className={styles.artistHero}>
            <img src={artistImage} alt={artistName} />
            <div className={styles.artistHeroContent}>
              <span>艺人主页占位</span>
              <h1>{artistName}</h1>
              <p>
                当前只保留搜索与艺人页 UI 入口。后续会接入真实艺人搜索与艺人详情接口。
              </p>
            </div>
          </section>
        ) : null}

        <TitleM>{artistName ? '继续浏览' : '搜索页'}</TitleM>
        <TextRegularM>
          搜索页当前仅保留 UI 骨架，不再使用前端静态分类卡片数据。
        </TextRegularM>
      </div>
    </div>
  )
}

export default Search
