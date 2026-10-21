package io.pagenote.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MermaidRendererAssetTest {
  @Test
  fun rendererAssetsArePinnedLocalAndStrict() {
    val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    val version = assets.open("mermaid/VERSION").bufferedReader().use { it.readText().trim() }
    val html = assets.open("mermaid/renderer.html").bufferedReader().use { it.readText() }
    val script = assets.open("mermaid/renderer.js").bufferedReader().use { it.readText() }
    val license = assets.open("mermaid/LICENSE").bufferedReader().use { it.readText() }
    val purifyLicense = assets.open("mermaid/DOMPurify-LICENSE").bufferedReader().use { it.readText() }
    val bundleSize = assets.open("mermaid/mermaid.min.js").use { it.readBytes().size }
    val purifySize = assets.open("mermaid/purify.min.js").use { it.readBytes().size }

    assertTrue(version.contains("mermaid=12.2.0"))
    assertTrue(version.contains("dompurify=3.4.17"))
    assertTrue(html.contains("mermaid.min.js"))
    assertTrue(html.contains("purify.min.js"))
    assertTrue(html.contains("renderer.js"))
    assertTrue(html.contains("default-src 'none'"))
    assertTrue(html.contains("connect-src 'none'"))
    assertTrue(!html.contains("https://") && !html.contains("http://"))
    assertTrue(script.contains("securityLevel: 'strict'"))
    assertTrue(script.contains("htmlLabels: false"))
    assertTrue(script.contains("startOnLoad: false"))
    assertTrue(script.contains("'suppressErrorRendering'"))
    assertTrue(script.contains("'htmlLabels'"))
    assertTrue(script.contains("'flowchart'"))
    assertTrue(script.contains("DOMPurify.sanitize"))
    assertTrue(script.contains("FORBID_ATTR: ['href', 'xlink:href']"))
    assertTrue(license.contains("The MIT License"))
    assertTrue(purifyLicense.contains("Mozilla Public License Version 2.0"))
    assertTrue("renderer bundle must be the full browser build", bundleSize > 1_000_000)
    assertTrue("SVG sanitizer bundle must be packaged", purifySize > 10_000)
  }
}
