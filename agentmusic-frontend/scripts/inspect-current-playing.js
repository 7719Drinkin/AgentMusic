import { mkdir } from 'node:fs/promises'
import { chromium } from 'playwright'

const BASE_URL = 'http://localhost:5173/'
const OUTPUT_DIR = new URL('../test-results/', import.meta.url)

async function run() {
  await mkdir(OUTPUT_DIR, { recursive: true })

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } })

  await page.goto(BASE_URL, { waitUntil: 'networkidle' })

  const openPanelButton = page.getByRole('button', { name: '打开当前播放栏' }).first()
  await openPanelButton.click()
  await page.waitForTimeout(300)

  await page.screenshot({
    path: new URL('./current-playing-panel.png', OUTPUT_DIR),
    fullPage: false,
  })

  const queueButton = page.getByRole('button', { name: '打开队列' }).first()
  await queueButton.click()
  await page.waitForTimeout(300)

  await page.screenshot({
    path: new URL('./current-playing-queue.png', OUTPUT_DIR),
    fullPage: false,
  })

  console.log(`Saved screenshots to: ${new URL('./', OUTPUT_DIR).pathname}`)
  await browser.close()
}

run().catch((error) => {
  console.error(error)
  process.exit(1)
})
