import { mkdir } from 'node:fs/promises'
import { chromium } from 'playwright'

const BASE_URL = 'http://localhost:5173/'
const OUTPUT_DIR = new URL('../test-results/', import.meta.url)

async function run() {
  await mkdir(OUTPUT_DIR, { recursive: true })

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } })

  await page.goto(BASE_URL, { waitUntil: 'networkidle' })
  await page.screenshot({
    path: new URL('./home.png', OUTPUT_DIR),
    fullPage: true,
  })

  console.log(`Saved screenshot: ${new URL('./home.png', OUTPUT_DIR).pathname}`)
  await browser.close()
}

run().catch((error) => {
  console.error(error)
  process.exit(1)
})
