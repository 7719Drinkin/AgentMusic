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
    const page = context.pages()[0] ?? await context.newPage()
    return { browser, context, page, ownsBrowser: false, ownsContext: false }
  } catch {
    const browser = await chromium.launch({ headless: true })
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    const page = await context.newPage()
    return { browser, context, page, ownsBrowser: true, ownsContext: true }
  }
}

async function waitForPlaylistCards(page) {
  const playlistCards = page.locator('button').filter({ hasText: PLAYLIST_META_TEXT })
  await playlistCards.first().waitFor({ timeout: 15000 })
  return playlistCards
}

async function run() {
  const { browser, context, page, ownsBrowser, ownsContext } = await openBrowser()

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
    const songRows = page.locator('button').filter({
      has: page.locator('img'),
      has: page.locator('strong'),
    })
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
      currentUrl: page.url(),
      navigatedPlaylistId,
      openedLatestPlaylist: expectedPlaylistId === navigatedPlaylistId,
      playlistTitle,
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
