import { connect } from "react-redux";
import * as Icons from '../icons';
import TextRegularM from '../text/text-regular-m';
import IconButton from '../buttons/icon-button';

import styles from "./footer-left.module.css";

function FooterLeft(props){
    return (
        <div className={styles.footerLeft}>
            <ImgBox 
                trackData={props.trackData}
            />
            <SongDetails 
                trackData={props.trackData}
            />
            <IconButton icon={<Icons.Like />} activeicon={<Icons.LikeActive />}/>
            <IconButton icon={<Icons.Corner />} activeicon={<Icons.Corner />}/>
        </div>
    );
}

function ImgBox({ trackData }){
    const coverSrc = trackData.trackImg || '/image/Playlist/liked-songs.PNG';

    return (
        <div className={styles.imgBox}>
            <img src={coverSrc} alt={trackData.trackName || '当前曲目封面'}/>
        </div>
    );
}

function SongDetails({ trackData }){
    return (
        <div className={styles.songDetails}>
            <TextRegularM>{trackData.trackName}</TextRegularM>
            <TextRegularM><small>{trackData.trackArtist}</small></TextRegularM>
        </div>
    );
}


const mapStateToProps = (state) => {
    return {
      trackData: state.trackData
    };
};
  
export default connect(mapStateToProps)(FooterLeft);
