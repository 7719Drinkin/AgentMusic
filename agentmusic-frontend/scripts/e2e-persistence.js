import { chromium } from 'playwright'

const FRONTEND_URL = 'http://localhost:5173/'
const BACKEND_URL = 'http://localhost:8080'
const CDP_URL = 'http://127.0.0.1:9222'
const TEST_QUERY = '\u6765\u70b9\u9002\u5408\u96e8\u5929\u901a\u52e4\u7684\u4e2d\u6587\u6b4c'
const PLAYLIST_META_TEXT = '\u63a8\u8350\u6b4c\u5355'
const TIMEOUT_MS = 60000

async function withTimeout(task) {
  let timer
  try {
    return await Promise.race([
      task(),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`E2E timeout after ${TIMEOUT_MS}ms`)), TIMEOUT_MS)
      }),
    ])
  } finally {
    clearTimeout(timer)
  }
}

async function openBrowser() {
  try {
    const browser = await chromium.connectOverCDP(CDP_URL)
    const context = browser.contexts()[0]
    if (!context) {
      throw new Error('No browser context available from CDP session')
    }
    const page = await context.newPage()
    await page.setViewportSize({ width: 1440, height: 960 })
    return { browser, context, page, ownsBrowser: false, ownsContext: false, ownsPage: true }
  } catch {
    const browser = await chromium.launch({ headless: true })
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    const page = await context.newPage()
    return { browser, context, page, ownsBrowser: true, ownsContext: true, ownsPage: false }
  }
}

async function waitForPlaylistCards(page) {
  const playlistCards = page.getByTestId('sidebar-playlist-card')
  await playlistCards.first().waitFor({ timeout: 15000 })
  return playlistCards
}

async function run() {
  const { browser, context, page, ownsBrowser, ownsContext, ownsPage } = await openBrowser()

  try {
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
    await page.locator('textarea').first().waitFor({ timeout: 15000 })

    const textarea = page.locator('textarea').first()
    await textarea.fill(TEST_QUERY)

    const chatResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/agent/chat')
        && response.request().method() === 'POST',
      { timeout: 20000 },
    )

    const composer = textarea.locator('xpath=ancestor::div[contains(@class, "InputShell")][1]')
    await composer.locator('button').last().click()
    const chatResponse = await chatResponsePromise
    const chatPayload = await chatResponse.json()
    const expectedPlaylistId = chatPayload.recommendedPlaylists?.[0]?.id ?? null

    const playlistRefreshPromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/playlists/demo-user?limit=10')
        && response.request().method() === 'GET',
      { timeout: 20000 },
    ).catch(() => null)

    await playlistRefreshPromise

    const playlistCards = await waitForPlaylistCards(page)
    const playlistCardCount = await playlistCards.count()
    const sidebarMetrics = await page.evaluate(() => {
      const scrollContainer = document.querySelector('[data-testid="sidebar-playlist-scroll"]')
      const playlistList = document.querySelector('[data-testid="sidebar-playlist-list"]')

      if (!(scrollContainer instanceof HTMLElement) || !(playlistList instanceof HTMLElement)) {
        return null
      }

      const containerStyle = window.getComputedStyle(scrollContainer)
      const listRect = playlistList.getBoundingClientRect()
      const containerRect = scrollContainer.getBoundingClientRect()

      return {
        overflowY: containerStyle.overflowY,
        clientHeight: scrollContainer.clientHeight,
        scrollHeight: scrollContainer.scrollHeight,
        listBottom: listRect.bottom,
        containerBottom: containerRect.bottom,
      }
    })

    if (!sidebarMetrics) {
      throw new Error('Sidebar playlist scroll container not found')
    }

    await page.evaluate(() => {
      const scrollContainer = document.querySelector('[data-testid="sidebar-playlist-scroll"]')
      if (scrollContainer instanceof HTMLElement) {
        scrollContainer.scrollTop = scrollContainer.scrollHeight
      }
    })

    const lastSidebarCardVisibleAfterScroll = await page.evaluate(() => {
      const scrollContainer = document.querySelector('[data-testid="sidebar-playlist-scroll"]')
      const cards = document.querySelectorAll('[data-testid="sidebar-playlist-card"]')
      const lastCard = cards[cards.length - 1]

      if (!(scrollContainer instanceof HTMLElement) || !(lastCard instanceof HTMLElement)) {
        return false
      }

      const scrollRect = scrollContainer.getBoundingClientRect()
      const cardRect = lastCard.getBoundingClientRect()
      return cardRect.bottom <= scrollRect.bottom && cardRect.top >= scrollRect.top
    })

    if (sidebarMetrics.overflowY === 'visible') {
      throw new Error(`Expected sidebar scroll container to be scrollable, got overflowY=${sidebarMetrics.overflowY}`)
    }

    if (!lastSidebarCardVisibleAfterScroll) {
      throw new Error('Last sidebar playlist card is still not visible after scrolling to the bottom')
    }

    await playlistCards.first().click()

    await page.waitForURL(/\/playlist\//, { timeout: 15000 })
    await page.locator('h1').first().waitFor({ timeout: 15000 })
    await page.waitForFunction(
      () => {
        const heading = document.querySelector('h1')
        return Boolean(heading && heading.textContent && heading.textContent.trim() !== '\u6b4c\u5355\u8be6\u60c5')
      },
      { timeout: 15000 },
    )

    const playlistTitle = (await page.locator('h1').first().textContent())?.trim() || ''
    const deviceToggle = page.getByTestId('playback-device-toggle')
    let devicePanelCount = 0
    let currentDeviceName = ''
    let deviceSummaryText = ''
    let deviceSwitchAttempted = false
    let deviceSwitchSkippedReason = ''
    let switchedDeviceName = ''
    let transferResponseStatus = null
    let switchedSessionDeviceId = null
    if (await page.getByTestId('playback-device-summary').count()) {
      deviceSummaryText = ((await page.getByTestId('playback-device-summary').textContent()) || '').trim()
    }
    if (await deviceToggle.count()) {
      await deviceToggle.click()
      await page.getByTestId('playback-device-panel').waitFor({ timeout: 10000 })
      await page.waitForFunction(
        () => {
          const panel = document.querySelector('[data-testid="playback-device-panel"]')
          if (!panel) {
            return false
          }
          if (panel.querySelector('[data-testid="playback-device-loading"]')) {
            return false
          }
          return panel.querySelectorAll('[data-testid="playback-device-item"]').length > 0
            || Boolean(panel.querySelector('[data-testid="playback-device-empty"]'))
        },
        { timeout: 10000 },
      )
      const deviceItems = page.getByTestId('playback-device-item')
      devicePanelCount = await deviceItems.count()
      const currentDevice = page.locator('[data-testid="playback-device-item"][data-current="true"]').first()
      if (await currentDevice.count()) {
        currentDeviceName = ((await currentDevice.getByTestId('playback-device-name').textContent()) || '').trim()
      }

      const switchCandidate = page.locator('[data-testid="playback-device-item"][data-current="false"][data-restricted="false"]').first()
      if (await switchCandidate.count()) {
        deviceSwitchAttempted = true
        switchedDeviceName = ((await switchCandidate.getByTestId('playback-device-name').textContent()) || '').trim()
        const targetDeviceId = await switchCandidate.getAttribute('data-device-id')
        const transferResponsePromise = page.waitForResponse(
          (response) =>
            response.url().includes('/api/playback/demo-user/transfer')
            && response.request().method() === 'POST',
          { timeout: 20000 },
        )
        await switchCandidate.click()
        const transferResponse = await transferResponsePromise
        transferResponseStatus = transferResponse.status()
        await page.waitForFunction(
          () => !document.querySelector('[data-testid="playback-device-panel"]'),
          { timeout: 10000 },
        )
        const switchedSessionResponse = await page.request.get(`${BACKEND_URL}/api/playback/demo-user/session`)
        const switchedSessionPayload = await switchedSessionResponse.json()
        switchedSessionDeviceId = switchedSessionPayload.deviceId ?? null
        if (targetDeviceId && switchedSessionDeviceId !== targetDeviceId) {
          throw new Error(`Expected switched device ${targetDeviceId}, received ${switchedSessionDeviceId}`)
        }
      } else {
        deviceSwitchSkippedReason = devicePanelCount > 0
          ? 'no-secondary-device'
          : 'no-device-items'
      }
    }

    const songRows = page.getByTestId('playlist-song-row')
    await songRows.first().waitFor({ timeout: 15000 })
    const songRowCount = await songRows.count()
    const firstSongTitle = songRowCount > 0
      ? ((await songRows.first().locator('strong').textContent())?.trim() || '')
      : ''

    let playResponseStatus = null
    let playResponseBody = null
    if (songRowCount > 0) {
      const playResponsePromise = page.waitForResponse(
        (response) =>
          response.url().includes('/api/playback/demo-user/play')
          && response.request().method() === 'POST',
        { timeout: 20000 },
      )

      await songRows.first().click()
      const playResponse = await playResponsePromise
      playResponseStatus = playResponse.status()
      playResponseBody = await playResponse.text()
    }

    const sessionResponse = await page.request.get(`${BACKEND_URL}/api/playback/demo-user/session`)
    const sessionPayload = await sessionResponse.json()
    const devicesResponse = await page.request.get(`${BACKEND_URL}/api/playback/demo-user/devices`)
    const devicesPayload = await devicesResponse.json()
    const navigatedPlaylistId = page.url().split('/playlist/')[1] ?? null

    console.log(JSON.stringify({
      chatStatus: chatResponse.status(),
      chatBody: chatPayload,
      expectedPlaylistId,
      recommendedPlaylists: Array.isArray(chatPayload.recommendedPlaylists)
        ? chatPayload.recommendedPlaylists.length
        : 0,
      playlistCardCount,
      sidebarOverflowY: sidebarMetrics.overflowY,
      sidebarClientHeight: sidebarMetrics.clientHeight,
      sidebarScrollHeight: sidebarMetrics.scrollHeight,
      lastSidebarCardVisibleAfterScroll,
      currentUrl: page.url(),
      navigatedPlaylistId,
      openedLatestPlaylist: expectedPlaylistId === navigatedPlaylistId,
      playlistTitle,
      devicePanelCount,
      currentDeviceName,
      deviceSummaryText,
      deviceSwitchAttempted,
      deviceSwitchSkippedReason,
      switchedDeviceName,
      transferResponseStatus,
      switchedSessionDeviceId,
      songRowCount,
      firstSongTitle,
      playResponseStatus,
      playResponseBody,
      deviceCount: Array.isArray(devicesPayload) ? devicesPayload.length : 0,
      sessionTrackId: sessionPayload.currentTrackId ?? null,
      sessionPlaylistId: sessionPayload.currentPlaylistId ?? null,
      sessionTrackIndex: sessionPayload.currentTrackIndex ?? null,
    }, null, 2))
  } finally {
    if (ownsPage) {
      await page.close()
    }
    if (ownsContext) {
      await context.close()
    }
    if (ownsBrowser) {
      await browser.close()
    }
  }
}

withTimeout(run)
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
