import React, { useRef, useEffect, useState } from 'react';
import { connect } from 'react-redux';
import { changeTrack, syncPlaybackSession as syncPlaybackSessionAction } from '../../actions';
import useWindowSize from '../../hooks/useWindowSize';
import FooterLeft from './footer-left';
import MusicControlBox from './player/music-control-box';
import MusicProgressBar from './player/music-progress-bar';
import FooterRight from './footer-right';
import Audio from './audio';
import {
    changePlaybackMode,
    fetchPlaybackSession,
    nextTrack,
    pausePlayback,
    playTrack,
    previousTrack,
    seekPlayback,
    syncPlaybackSession
} from '../../api/playback';
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

    const applyPlaybackSession = async (session) => {
        if (!session) {
            return;
        }

        let payload = {
            currentPositionMs: session.currentPositionMs,
            isPlaying: session.isPlaying,
            playbackMode: session.playbackMode,
            deviceId: session.deviceId
        };

        if (session.currentTrackId) {
            const track = await fetchTrack(session.currentTrackId);
            if (track) {
                payload = {
                    ...payload,
                    trackId: track.trackId,
                    track: track.previewUrl || props.trackData.track,
                    trackName: track.title,
                    trackImg: track.albumImageUrl || props.trackData.trackImg,
                    trackArtist: track.artistId || 'Spotify 曲目',
                    durationMs: track.durationMs
                };
            }
        }

        props.syncPlaybackSessionAction(payload);
        setCurrentTime((session.currentPositionMs || 0) / 1000);
    };

    const refreshPlaybackSession = async (useSyncEndpoint = false) => {
        try {
            const session = useSyncEndpoint
                ? await syncPlaybackSession(DEMO_USER_ID)
                : await fetchPlaybackSession(DEMO_USER_ID);
            await applyPlaybackSession(session);
        } catch {
        }
    };

    useEffect(() => {
        let cancelled = false;

        const loadPlaybackSession = async () => {
            if (cancelled) {
                return;
            }
            await refreshPlaybackSession(false);
        };

        loadPlaybackSession();
        window.addEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession);

        return () => {
            cancelled = true;
            window.removeEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession);
        };
    }, []);

    useEffect(() => {
        if (!audioRef.current || !props.trackData.track) {
          return;
        }

        if (props.isPlaying) {
          audioRef.current.play().catch(() => {});
        } else {
          audioRef.current.pause();
        }
    }, [props.trackData.track, props.isPlaying]);

    useEffect(() => {
        if (audioRef.current) {
            audioRef.current.volume = volume;
        }
    }, [volume]);

    useEffect(() => {
        if (!audioRef.current) {
            return;
        }

        const handleEnded = async () => {
            if (props.trackData.trackId) {
                await handleNext();
                return;
            }

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
    }, [props.trackData.trackKey, props.trackData.trackId]);

    const handleTrackClick = async (position) => {
        if (audioRef.current) {
            audioRef.current.currentTime = position;
        }
        setCurrentTime(position);

        if (props.trackData.trackId) {
            try {
                const session = await seekPlayback(
                    DEMO_USER_ID,
                    Math.round(position * 1000),
                    props.deviceId
                );
                await applyPlaybackSession(session);
            } catch {
            }
        }
    };

    const handleTogglePlay = async () => {
        if (!props.trackData.trackId) {
            props.syncPlaybackSessionAction({
                isPlaying: !props.isPlaying,
                currentPositionMs: Math.round(currentTime * 1000),
                playbackMode: props.playbackMode,
                deviceId: props.deviceId
            });
            return;
        }

        try {
            const session = props.isPlaying
                ? await pausePlayback(DEMO_USER_ID, props.deviceId)
                : await playTrack(DEMO_USER_ID, {
                    trackId: props.trackData.trackId,
                    deviceId: props.deviceId,
                    playbackMode: props.playbackMode
                });
            await applyPlaybackSession(session);
        } catch {
        }
    };

    const handleNext = async () => {
        if (!props.trackData.trackId) {
            if(props.trackData.trackKey[1] === (PLAYLIST[props.trackData.trackKey[0]].playlistData.length)-1){ return; }
            props.changeTrack([props.trackData.trackKey[0], parseInt(props.trackData.trackKey[1])+1]);
            return;
        }

        try {
            await nextTrack(DEMO_USER_ID, props.deviceId);
            await refreshPlaybackSession(true);
        } catch {
        }
    };

    const handlePrevious = async () => {
        if (!props.trackData.trackId) {
            if(props.trackData.trackKey[1] === 0){ return; }
            props.changeTrack([props.trackData.trackKey[0], props.trackData.trackKey[1]-1]);
            return;
        }

        try {
            await previousTrack(DEMO_USER_ID, props.deviceId);
            await refreshPlaybackSession(true);
        } catch {
        }
    };

    const handleToggleShuffle = async () => {
        const nextMode = props.playbackMode === 'SHUFFLE' ? 'SEQUENTIAL' : 'SHUFFLE';
        await updatePlaybackMode(nextMode);
    };

    const handleCycleLoopMode = async () => {
        let nextMode = 'LIST_LOOP';
        if (props.playbackMode === 'LIST_LOOP') {
            nextMode = 'SINGLE_LOOP';
        } else if (props.playbackMode === 'SINGLE_LOOP') {
            nextMode = 'SEQUENTIAL';
        }
        await updatePlaybackMode(nextMode);
    };

    const updatePlaybackMode = async (nextMode) => {
        if (!props.trackData.trackId) {
            props.syncPlaybackSessionAction({
                isPlaying: props.isPlaying,
                currentPositionMs: Math.round(currentTime * 1000),
                playbackMode: nextMode,
                deviceId: props.deviceId
            });
            return;
        }

        try {
            const session = await changePlaybackMode(DEMO_USER_ID, nextMode, props.deviceId);
            await applyPlaybackSession(session);
        } catch {
        }
    };

    return (
        <footer className={styles.footer}>
            <div className={styles.nowplayingbar}>
                <FooterLeft />
                <div className={styles.footerMid}>
                    <MusicControlBox
                        isPlaying={props.isPlaying}
                        playbackMode={props.playbackMode}
                        onTogglePlay={handleTogglePlay}
                        onPrevious={handlePrevious}
                        onNext={handleNext}
                        onToggleShuffle={handleToggleShuffle}
                        onCycleLoopMode={handleCycleLoopMode}
                    />
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
        currentPositionMs: state.currentPositionMs,
        playbackMode: state.playbackMode,
        deviceId: state.deviceId
    };
};
  
export default connect(mapStateToProps, { changeTrack, syncPlaybackSessionAction })(Footer);
