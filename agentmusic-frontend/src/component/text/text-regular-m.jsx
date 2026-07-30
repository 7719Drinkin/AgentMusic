import styles from './text-regular-m.module.css'

function TextRegularM({children, className = ''}){
    return (
        <p className={`${styles.text} ${className}`.trim()}>
            {children}
        </p>
    );
}

export default TextRegularM;
