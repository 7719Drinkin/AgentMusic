import { useEffect, useMemo, useState } from 'react'
import { connect } from 'react-redux'
import { useHistory, useLocation } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { fetchArtist } from '../api/music'
import { fetchPlaybackDevices, playTrack } from '../api/playback'
import { fetchRecentPlaylists } from '../api/playlists'
import Topnav from '../component/topnav/topnav'
import TextRegularM from '../component/text/text-regular-m'
import TitleL from '../component/text/title-l'
import TitleM from '../component/text/title-m'
import styles from './home.module.css'

const DEMO_USER_ID = 'demo-user'
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated'
const PLAYLIST_REFRESH_EVENT = 'agentmusic:playlists-updated'
const INITIAL_COLLAPSED_SECTIONS = {
  playlists: false,
  tracks: false,
  artists: false,
  albums: false,
}

function Home({ playbackMode, deviceId }) {
  const history = useHistory()
  const location = useLocation()
  const [playlists, setPlaylists] = useState([])
  const [artists, setArtists] = useState({})
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [playbackError, setPlaybackError] = useState('')
  const [isPlaybackBusy, setIsPlaybackBusy] = useState(false)
  const [collapsedSections, setCollapsedSections] = useState(INITIAL_COLLAPSED_SECTIONS)

  useEffect(() => {
    let cancelled = false

    async function loadHomeData() {
      setIsLoading(true)
      try {
        const recentPlaylists = await fetchRecentPlaylists(DEMO_USER_ID, 10)
        if (cancelled) {
          return
        }

        setPlaylists(recentPlaylists)
        setErrorMessage('')

        const artistIds = [...new Set(
          recentPlaylists
            .flatMap((playlist) => playlist.tracks ?? [])
            .map((item) => item?.track?.artistId)
            .filter(Boolean),
        )]

        if (artistIds.length === 0) {
          setArtists({})
          return
        }

        const artistEntries = await Promise.all(
          artistIds.map(async (artistId) => {
            try {
              const artist = await fetchArtist(artistId)
              return [artistId, artist]
            } catch {
              return [artistId, null]
            }
          }),
        )

        if (!cancelled) {
          setArtists(Object.fromEntries(artistEntries))
        }
      } catch (error) {
        if (!cancelled) {
          setPlaylists([])
          setArtists({})
          setErrorMessage(getErrorMessage(error, 'Failed to load Music Home.'))
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadHomeData()
    window.addEventListener(PLAYLIST_REFRESH_EVENT, loadHomeData)

    return () => {
      cancelled = true
      window.removeEventListener(PLAYLIST_REFRESH_EVENT, loadHomeData)
    }
  }, [])

  const flattenedTracks = useMemo(
    () => playlists.flatMap((playlist) =>
      (playlist.tracks ?? []).map((item, index) => ({
        playlistId: playlist.id,
        playlistName: playlist.name,
        trackIndex: index,
        addedAt: item.addedAt,
        track: item.track,
      }))),
    [playlists],
  )

  const playlistHighlights = useMemo(() => playlists.slice(0, 6), [playlists])

  const trackHighlights = useMemo(() => {
    const seen = new Set()
    const result = []

    for (const item of flattenedTracks) {
      const trackId = item.track?.trackId
      if (!trackId || seen.has(trackId)) {
        continue
      }

      seen.add(trackId)
      result.push(item)
      if (result.length >= 8) {
        break
      }
    }

    return result
  }, [flattenedTracks])

  const artistHighlights = useMemo(() => {
    const summary = new Map()

    flattenedTracks.forEach((item) => {
      const track = item.track
      const artistId = track?.artistId
      if (!artistId) {
        return
      }

      const artistData = artists[artistId]
      const existing = summary.get(artistId)
      if (existing) {
        existing.trackCount += 1
        return
      }

      summary.set(artistId, {
        artistId,
        name: artistData?.name || 'Unknown artist',
        imageUrl: artistData?.imageUrl || track?.albumImageUrl || '/image/Playlist/liked-songs.PNG',
        bio: artistData?.bio || '',
        followers: artistData?.followers || 0,
        trackCount: 1,
        latestTrackTitle: track?.title || '',
      })
    })

    return [...summary.values()]
      .sort((left, right) => right.trackCount - left.trackCount)
      .slice(0, 6)
  }, [artists, flattenedTracks])

  const albumHighlights = useMemo(() => {
    const summary = new Map()

    flattenedTracks.forEach((item) => {
      const track = item.track
      const albumKey = track?.albumId || track?.albumName
      if (!albumKey || summary.has(albumKey)) {
        return
      }

      summary.set(albumKey, {
        albumId: track.albumId || albumKey,
        albumName: track.albumName || 'Unknown album',
        artistName: artists[track.artistId]?.name || 'Unknown artist',
        imageUrl: track.albumImageUrl || '/image/Playlist/liked-songs.PNG',
        sourcePlaylistName: item.playlistName,
      })
    })

    return [...summary.values()].slice(0, 6)
  }, [artists, flattenedTracks])

  const focusedArtistQuery = useMemo(() => {
    const params = new URLSearchParams(location.search)
    return params.get('artist')?.trim() || ''
  }, [location.search])

  const focusedArtist = useMemo(() => {
    if (focusedArtistQuery) {
      const matchedArtist = artistHighlights.find(
        (artist) => artist.name.toLowerCase() === focusedArtistQuery.toLowerCase(),
      )

      if (matchedArtist) {
        return matchedArtist
      }

      return {
        artistId: '',
        name: focusedArtistQuery,
        imageUrl: '/image/Playlist/liked-songs.PNG',
        bio: 'Agent has not fetched richer biography metadata for this artist yet. Open Agent Chat to generate a new recommendation thread around this artist.',
        followers: 0,
        trackCount: 0,
        latestTrackTitle: '',
      }
    }

    return artistHighlights[0] || null
  }, [artistHighlights, focusedArtistQuery])

  const latestPlaylist = playlistHighlights[0] || null
  const heroTitle = focusedArtist
    ? `${focusedArtist.name} spotlight`
    : latestPlaylist?.name || 'Music Home'
  const heroSummary = focusedArtist
    ? focusedArtist.bio || `${focusedArtist.name} appears in ${focusedArtist.trackCount} recent recommendation track${focusedArtist.trackCount === 1 ? '' : 's'}.`
    : 'Browse the latest songs, artists, playlists, and albums surfaced by recent Agent recommendation sessions.'

  const statItems = [
    { label: 'Recent playlists', value: playlistHighlights.length },
    { label: 'Recommended tracks', value: trackHighlights.length },
    { label: 'Featured artists', value: artistHighlights.length },
    { label: 'Albums surfaced', value: albumHighlights.length },
  ]

  const toggleSection = (sectionKey) => {
    setCollapsedSections((current) => ({
      ...current,
      [sectionKey]: !current[sectionKey],
    }))
  }

  const loadAvailableDevices = async () => {
    const devices = await fetchPlaybackDevices(DEMO_USER_ID)
    return Array.isArray(devices) ? devices : []
  }

  const handlePlayTrack = async (item) => {
    if (!item?.track?.trackId || isPlaybackBusy) {
      return
    }

    try {
      setIsPlaybackBusy(true)
      setPlaybackError('')

      const devices = await loadAvailableDevices()
      if (devices.length === 0) {
        setPlaybackError('No active Spotify device is available. Enable AgentMusic Web Player and try again.')
        return
      }

      await playTrack(DEMO_USER_ID, {
        trackId: item.track.trackId,
        playlistId: item.playlistId,
        trackIndex: item.trackIndex,
        deviceId,
        playbackMode,
      })

      window.dispatchEvent(new CustomEvent(PLAYBACK_REFRESH_EVENT))
    } catch (error) {
      setPlaybackError(getErrorMessage(error, 'Failed to start playback from Music Home.'))
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  const handleArtistFocus = (artistName) => {
    history.push(`/music?artist=${encodeURIComponent(artistName)}`)
  }

  const renderSectionCard = ({
    sectionKey,
    title,
    description,
    countLabel,
    testId,
    toggleTestId,
    collapsedHint,
    body,
  }) => {
    const isCollapsed = collapsedSections[sectionKey]

    return (
      <section
        className={`${styles.SectionCard} ${isCollapsed ? styles.SectionCardCollapsed : ''}`.trim()}
        data-testid={testId}
      >
        <div className={styles.SectionCardHeader}>
          <div className={styles.SectionHeader}>
            <TitleM>{title}</TitleM>
            <TextRegularM>{description}</TextRegularM>
          </div>

          <div className={styles.SectionHeaderActions}>
            <span className={styles.SectionCountPill}>{countLabel}</span>
            <button
              className={styles.SectionToggle}
              type="button"
              onClick={() => toggleSection(sectionKey)}
              aria-expanded={!isCollapsed}
              data-testid={toggleTestId}
            >
              {isCollapsed ? 'Expand' : 'Collapse'}
            </button>
          </div>
        </div>

        {isCollapsed ? (
          <div className={styles.SectionCollapsedHint}>
            <TextRegularM>{collapsedHint}</TextRegularM>
          </div>
        ) : (
          <div className={styles.SectionBody}>{body}</div>
        )}
      </section>
    )
  }

  return (
    <div className={styles.Home} data-testid="music-home-page">
      <div className={styles.HoverBg}></div>
      <div className={styles.Bg}></div>

      <Topnav />

      <div className={styles.Content}>
        <section className={styles.Hero} data-testid="music-home-hero">
          <div className={styles.HeroCopyCard}>
            <div className={styles.HeroEyebrowRow}>
              <span className={styles.HeroEyebrow}>Music Home</span>
              <span className={styles.HeroPill}>Agent curated</span>
            </div>
            <div className={styles.HeroTitleBlock}>
              <span className={styles.HeroKicker}>Recommendation canvas</span>
              <TitleL>{heroTitle}</TitleL>
            </div>
            <TextRegularM className={styles.HeroLead}>
              {isLoading ? 'Preparing the latest recommendation view...' : errorMessage || heroSummary}
            </TextRegularM>

            <div className={styles.HeroActions}>
              <button
                className={styles.PrimaryAction}
                type="button"
                onClick={() => history.push('/')}
              >
                Open Agent Chat
              </button>
              {latestPlaylist ? (
                <button
                  className={styles.SecondaryAction}
                  type="button"
                  onClick={() => history.push(`/playlist/${encodeURIComponent(latestPlaylist.id)}`)}
                >
                  Open latest playlist
                </button>
              ) : null}
            </div>
          </div>

          <div className={styles.HeroAsideStack}>
            {focusedArtist ? (
              <div className={styles.SpotlightCard} data-testid="music-home-artist-spotlight">
                <img src={focusedArtist.imageUrl} alt={focusedArtist.name} />
                <div className={styles.SpotlightMeta}>
                  <span>Artist spotlight</span>
                  <strong>{focusedArtist.name}</strong>
                  <p>
                    {focusedArtist.followers
                      ? `${new Intl.NumberFormat('en-US').format(focusedArtist.followers)} Spotify followers`
                      : 'Agent-focused artist context'}
                  </p>
                </div>
              </div>
            ) : (
              <div className={styles.SpotlightCardEmpty} data-testid="music-home-artist-spotlight-empty">
                <strong>No artist spotlight yet</strong>
                <p>Generate more recommendations in Agent Chat to populate artist, album, and track highlights here.</p>
              </div>
            )}

            <div className={styles.SignalCard} data-testid="music-home-stats">
              <div className={styles.SignalCardHeader}>
                <span>Current recommendation inventory</span>
                <strong>Live session snapshot</strong>
              </div>
              <div className={styles.HeroMetricGrid}>
                {statItems.map((item) => (
                  <div key={item.label} className={styles.HeroMetricCard}>
                    <span>{item.label}</span>
                    <strong>{item.value}</strong>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        {playbackError ? (
          <section className={styles.StatusBanner}>
            <TextRegularM>{playbackError}</TextRegularM>
          </section>
        ) : null}

        {renderSectionCard({
          sectionKey: 'playlists',
          title: 'Latest recommendation playlists',
          description: 'Recent playlist outputs from the agent, preserved in playback order.',
          countLabel: `${playlistHighlights.length} playlists`,
          testId: 'music-home-playlists',
          toggleTestId: 'music-home-playlists-toggle',
          collapsedHint: 'Playlist highlights are collapsed. Expand to inspect the latest generated recommendation sets.',
          body: (
            <div className={styles.CardGrid}>
              {playlistHighlights.map((playlist) => {
                const cover = playlist.tracks?.[0]?.track?.albumImageUrl || '/image/Playlist/liked-songs.PNG'
                return (
                  <button
                    key={playlist.id}
                    className={styles.MediaCard}
                    type="button"
                    onClick={() => history.push(`/playlist/${encodeURIComponent(playlist.id)}`)}
                    data-testid="music-home-playlist-card"
                  >
                    <img src={cover} alt={playlist.name} />
                    <div className={styles.MediaCardMeta}>
                      <strong>{playlist.name}</strong>
                      <span>{playlist.tracks?.length ?? 0} tracks | version {playlist.version}</span>
                    </div>
                  </button>
                )
              })}
            </div>
          ),
        })}

        {renderSectionCard({
          sectionKey: 'tracks',
          title: 'Tracks surfacing in recent sessions',
          description: 'Click a track card to start playback from its source playlist context.',
          countLabel: `${trackHighlights.length} tracks`,
          testId: 'music-home-tracks',
          toggleTestId: 'music-home-tracks-toggle',
          collapsedHint: 'Track highlights are collapsed. Expand to queue or inspect recent surfaced songs.',
          body: (
            <div className={styles.TrackGrid}>
              {trackHighlights.map((item) => (
                <button
                  key={item.track.trackId}
                  className={styles.TrackCard}
                  type="button"
                  onClick={() => handlePlayTrack(item)}
                  disabled={isPlaybackBusy}
                  data-testid="music-home-track-card"
                >
                  <img src={item.track.albumImageUrl || '/image/Playlist/liked-songs.PNG'} alt={item.track.title} />
                  <div className={styles.TrackMeta}>
                    <strong>{item.track.title}</strong>
                    <span>{artists[item.track.artistId]?.name || 'Unknown artist'}</span>
                    <p>From {item.playlistName}</p>
                  </div>
                </button>
              ))}
            </div>
          ),
        })}

        {renderSectionCard({
          sectionKey: 'artists',
          title: 'Featured artists',
          description: 'Agent-related artist entities pulled from the latest playlists.',
          countLabel: `${artistHighlights.length} artists`,
          testId: 'music-home-artists',
          toggleTestId: 'music-home-artists-toggle',
          collapsedHint: 'Artist highlights are collapsed. Expand to switch the spotlight focus.',
          body: (
            <div className={styles.ArtistGrid}>
              {artistHighlights.map((artist) => (
                <button
                  key={artist.artistId || artist.name}
                  className={`${styles.ArtistCard} ${focusedArtist?.name === artist.name ? styles.ArtistCardActive : ''}`.trim()}
                  type="button"
                  onClick={() => handleArtistFocus(artist.name)}
                  data-testid="music-home-artist-card"
                >
                  <img src={artist.imageUrl} alt={artist.name} />
                  <div className={styles.ArtistMeta}>
                    <strong>{artist.name}</strong>
                    <span>{artist.trackCount} tracks in recent recommendations</span>
                    <p>{artist.latestTrackTitle ? `Latest surfaced track: ${artist.latestTrackTitle}` : 'Artist metadata pending'}</p>
                  </div>
                </button>
              ))}
            </div>
          ),
        })}

        {renderSectionCard({
          sectionKey: 'albums',
          title: 'Albums surfacing through recommendations',
          description: 'Album entities are currently inferred from track metadata returned by playlist detail APIs.',
          countLabel: `${albumHighlights.length} albums`,
          testId: 'music-home-albums',
          toggleTestId: 'music-home-albums-toggle',
          collapsedHint: 'Album highlights are collapsed. Expand to inspect the source albums behind recent sessions.',
          body: (
            <div className={styles.CardGrid}>
              {albumHighlights.map((album) => (
                <div key={album.albumId} className={styles.MediaCardStatic} data-testid="music-home-album-card">
                  <img src={album.imageUrl} alt={album.albumName} />
                  <div className={styles.MediaCardMeta}>
                    <strong>{album.albumName}</strong>
                    <span>{album.artistName}</span>
                    <p>{album.sourcePlaylistName}</p>
                  </div>
                </div>
              ))}
            </div>
          ),
        })}
      </div>
    </div>
  )
}

const mapStateToProps = (state) => ({
  playbackMode: state.playbackMode,
  deviceId: state.deviceId,
})

export default connect(mapStateToProps)(Home)
