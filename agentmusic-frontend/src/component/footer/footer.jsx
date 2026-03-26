import React, { useRef, useEffect, useState } from 'react';
import { connect } from 'react-redux';
import { changeTrack, changePlay, syncPlaybackSession } from '../../actions';
import useWindowSize from '../../hooks/useWindowSize';
import FooterLeft from './footer-left';
import MusicControlBox from './player/music-control-box';
import MusicProgressBar from './player/music-progress-bar';
import FooterRight from './footer-right';
import Audio from './audio';
import { fetchPlaybackSession } from '../../api/playback';
import { fetchTrack } from '../../api/music';

import { PLAYLIST } from "../../data/index";
import CONST from '../../constants/index';
import styles from "./footer.module.css";

const DEMO_USER_ID = 'demo-user';
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated';

function Footer(props){
    const size = useWindowSize();

    const [currentTime, setCurrentTime] = useState(0);
    const [duration, setDuration] = useState(0);
    const [volume, setVolume] = useState(1);
    const audioRef = useRef(null);

    const handleTrackClick = (position) => {
        audioRef.current.currentTime = position;
    };

    useEffect(() => {
        let cancelled = false;

        const loadPlaybackSession = async () => {
            try {
                const session = await fetchPlaybackSession(DEMO_USER_ID);
                if (cancelled || !session || !session.currentTrackId) {
                    return;
                }

                const track = await fetchTrack(session.currentTrackId);
                if (cancelled || !track) {
                    return;
                }

                props.syncPlaybackSession({
                    trackId: track.trackId,
                    track: track.previewUrl || '',
                    trackName: track.title,
                    trackImg: track.albumImageUrl,
                    trackArtist: track.artistId || 'Spotify 曲目',
                    durationMs: track.durationMs,
                    currentPositionMs: session.currentPositionMs,
                    isPlaying: session.isPlaying,
                    playbackMode: session.playbackMode,
                    deviceId: session.deviceId
                });
                setCurrentTime((session.currentPositionMs || 0) / 1000);
            } catch {
            }
        };

        loadPlaybackSession();
        window.addEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession);

        return () => {
            cancelled = true;
            window.removeEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession);
        };
    }, [props.syncPlaybackSession]);

    useEffect(() => {
        if (!audioRef.current || !props.trackData.track) {
          return;
        }

        if (props.isPlaying) {
          audioRef.current.play();
        } else {
          audioRef.current.pause();
        }
    }, [audioRef, props.isPlaying]);

    /*useEffect(() => {
        if (props.isPlaying) {
          localStorage.setItem('playedSong', audioRef.current.currentSrc);
        } else {
          localStorage.setItem('playedSong', 'stop');
        }
    });*/

    useEffect(() => {
        if (audioRef.current) {
            audioRef.current.volume = volume;
        }
    }, [audioRef, volume]);

    
    useEffect(() => {
        if (!audioRef.current) {
            return;
        }

        const handleEnded = () => {
            if (props.trackData.trackKey[0] < 0) {
                return;
            }

            if(props.trackData.trackKey[1] === (PLAYLIST[props.trackData.trackKey[0]].playlistData.length)-1){
                props.changeTrack([props.trackData.trackKey[0], 0])
            }else{
                props.changeTrack([props.trackData.trackKey[0], parseInt(props.trackData.trackKey[1])+1])
            }
        };

        audioRef.current.addEventListener('ended', handleEnded);
        return () => {
            audioRef.current?.removeEventListener('ended', handleEnded);
        };
    }, [props.changeTrack, props.trackData.trackKey]);

    return (
        <footer className={styles.footer}>
            <div className={styles.nowplayingbar}>
                <FooterLeft />
                <div className={styles.footerMid}>
                    <MusicControlBox />
                    <MusicProgressBar 
                        currentTime={currentTime} 
                        duration={duration || ((props.trackData.durationMs || 0) / 1000)} 
                        handleTrackClick={handleTrackClick}
                    />
                    <Audio
                        ref={audioRef}
                        handleDuration={setDuration}
                        handleCurrentTime={setCurrentTime}
                        trackData={props.trackData}
                        isPlaying={props.isPlaying}
                    />
                </div>
                {size.width > CONST.MOBILE_SIZE && 
                    <FooterRight 
                        volume={volume} 
                        setVolume={setVolume}
                    />
                }
            </div>
        </footer>
    );
}


const mapStateToProps = (state) => {
    return {
        trackData: state.trackData,
        isPlaying: state.isPlaying,
        currentPositionMs: state.currentPositionMs
    };
};
  
export default connect(mapStateToProps, { changeTrack, changePlay, syncPlaybackSession })(Footer);
