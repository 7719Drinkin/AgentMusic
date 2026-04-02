import PropTypes from 'prop-types';
import { useEffect, useRef, useState } from 'react';

import styles from "./range-slider.module.css";

function RangeSlider({ value, minvalue, maxvalue, handleChange, disabled = false }){
    const inputRef = useRef(null)
    const inputRefWidth = useRef(null)
    const[decimalValue, setDecimalValue] = useState(0);

    useEffect(() => {
        if (!inputRef.current) {
            return;
        }
        const inputWidth = window.getComputedStyle(inputRef.current).width
        inputRefWidth.current = parseInt(inputWidth.replace('px',''))
    })

    useEffect(() => {
        if(maxvalue > 1){
            setDecimalValue((value * 1) / maxvalue);
        }else{
            setDecimalValue(value);
        }
    })

    const handleInputChange = (e) => {
        if (disabled) {
            return;
        }
        handleChange(parseFloat(e.target.value));
    };

    return (
        <div className={`${styles.progressBar} ${disabled ? styles.disabledProgressBar : ''}`}>
            <input
                ref={inputRef}
                type="range"
                onChange={handleInputChange}
                className={styles.range__slider}
                min={minvalue}
                max={maxvalue}
                step="0.01"
                value={value}
                disabled={disabled}
            />
            <span
                className={styles.spanThumb}
                style={{left: `${(decimalValue * (inputRefWidth.current || 0)) - 3}px`}}
            >

            </span>
        </div>
    );
}

RangeSlider.propTypes = {
    maxvalue: PropTypes.number.isRequired,
    minvalue: PropTypes.number,
    handleChange: PropTypes.func.isRequired,
    value: PropTypes.number.isRequired,
    disabled: PropTypes.bool,
};

export default RangeSlider;
