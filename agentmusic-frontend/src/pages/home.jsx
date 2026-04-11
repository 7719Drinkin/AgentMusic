import Topnav from '../component/topnav/topnav'
import TitleL from '../component/text/title-l'
import TextRegularM from '../component/text/text-regular-m'
import styles from './home.module.css'

function Home() {
  return (
    <div className={styles.Home}>
      <div className={styles.HoverBg}></div>
      <div className={styles.Bg}></div>

      <Topnav />
      <div className={styles.Content}>
        <section>
          <div className={styles.SectionTitle}>
            <TitleL>音乐主页</TitleL>
          </div>

          <TextRegularM>
            当前页面保留为 UI 骨架。业务数据接入将统一通过后端接口完成，不再使用前端静态歌单。
          </TextRegularM>
        </section>
      </div>
    </div>
  )
}

export default Home
