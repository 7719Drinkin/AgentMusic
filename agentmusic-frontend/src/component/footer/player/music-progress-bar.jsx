import convertTime from '../../../functions/convertTime';

import TextRegularM from '../../text/text-regular-m';
import RangeSlider from '../range-slider';

import styles from "./music-progress-bar.module.css";

function MusicProgressBar({ currentTime, duration, handleTrackClick, disabled }){
    return (
        <div className={styles.musicProgress}>
            <span>
                <TextRegularM>{convertTime(currentTime)}</TextRegularM>
            </span>
            <RangeSlider
                value={currentTime}
                minvalue={0}
                maxvalue={duration || 0}
                handleChange={handleTrackClick}
                disabled={disabled || !duration}
            />
            <span>
                <TextRegularM>{convertTime(duration)}</TextRegularM>
            </span>
        </div>
    );
}

export default MusicProgressBar;
