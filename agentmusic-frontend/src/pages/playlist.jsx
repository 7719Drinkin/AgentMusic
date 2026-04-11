import { useParams } from 'react-router'
import Topnav from '../component/topnav/topnav'
import TextRegularM from '../component/text/text-regular-m'
import TitleL from '../component/text/title-l'
import styles from './playlist.module.css'

function PlaylistPage() {
  const { path } = useParams()

  return (
    <div className={styles.PlaylistPage}>
      <div className={styles.gradientBg}></div>
      <div className={styles.gradientBgSoft}></div>
      <div className={styles.Bg}></div>

      <Topnav />

      <div className={styles.PlaylistDetailsFallback}>
        <TitleL>歌单详情</TitleL>
        <TextRegularM>
          当前路由已保留，但歌单详情页不再读取前端静态歌单数据。后续将接入后端歌单详情接口。
        </TextRegularM>
        <TextRegularM>
          当前歌单标识：{path}
        </TextRegularM>
      </div>
    </div>
  )
}

export default PlaylistPage
