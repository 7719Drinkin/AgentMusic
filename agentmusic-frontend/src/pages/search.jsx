import { useLocation } from 'react-router-dom'
import Topnav from '../component/topnav/topnav'
import TitleM from '../component/text/title-m'
import SearchPageCard from '../component/cards/searchpage-card'
import { SEARCHCARDS } from '../data/index'
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
              <span>艺人主页（占位）</span>
              <h1>{artistName}</h1>
              <p>当前前端已支持从底部播放器和当前播放栏跳转到艺人页入口。后续接入真实艺人资料后，这里将显示艺人热门歌曲、简介与相关歌单。</p>
            </div>
          </section>
        ) : null}

        <TitleM>{artistName ? '继续浏览' : '浏览全部分类'}</TitleM>
        <div className={styles.SearchCardGrid}>
          {SEARCHCARDS.map((card) => (
            <SearchPageCard
              key={card.title}
              cardData={{
                bgcolor: card.bgcolor,
                title: card.title,
                imgurl: card.imgurl,
              }}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

export default Search
