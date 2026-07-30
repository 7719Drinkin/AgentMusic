import TitleM from '../component/text/title-m'
import Topnav from '../component/topnav/topnav'
import TextRegularM from '../component/text/text-regular-m'
import styles from './library.module.css'

function Library() {
  return (
    <div className={styles.LibPage}>
      <Topnav tabButtons={true} />
      <div className={styles.Library}>
        <TitleM>资料库</TitleM>
        <TextRegularM>
          资料库页面当前仅保留 UI 结构。后续会接入真实歌单、收藏和历史数据，不再依赖前端静态样例。
        </TextRegularM>
      </div>
    </div>
  )
}

export default Library
