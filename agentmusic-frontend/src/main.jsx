import React from 'react'
import ReactDOM from 'react-dom/client'
import { createStore } from 'redux'
import { Provider } from 'react-redux'
import { reducer } from './reducers/index'
import App from './App'
import { SpotifyWebPlaybackProvider } from './context/SpotifyWebPlaybackContext'
import './style/index.css'

const store = createStore(reducer)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Provider store={store}>
      <SpotifyWebPlaybackProvider>
        <App />
      </SpotifyWebPlaybackProvider>
    </Provider>
  </React.StrictMode>,
)
