package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookshelfRepositoryTest {
  @Test fun discoversNestedProjectsStopsAtProjectRootAndKeepsMalformedProjectsVisibleAsWarnings(): Unit = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val tree = ProjectCreateSafTestProvider.treeUri()
    val access = SafPreparedDocumentAccess(resolver)
    val base = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val folder = Uri.parse(access.createDirectory(base.toString(), "shelf-${UUID.randomUUID()}"))
    val folderTree = DocumentsContract.buildTreeDocumentUri(tree.authority, DocumentsContract.getDocumentId(folder))
    try {
      val md = ProjectCreator(resolver, context.cacheDir).create(folderTree, "Paper")
      val pdfFile = Uri.parse(access.createFile(folder.toString(), "application/pdf", "source.pdf"))
      val pdfDocument = PdfDocument()
      try {
        val pdf = pdfDocument
        repeat(2) { index ->
          val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
          page.canvas.drawColor(if (index == 0) Color.rgb(190, 40, 80) else Color.rgb(20, 80, 190))
          pdf.finishPage(page)
        }
        resolver.openOutputStream(pdfFile)!!.use(pdf::writeTo)
      } finally { pdfDocument.close() }
      val pdf = ProjectCreator(resolver, context.cacheDir).create(folderTree, "PDF", pdfFile, "source.pdf")
      val broken = access.createDirectory(folder.toString(), "broken")
      val manifest = access.createFile(broken, "application/json", "project.pagenote")
      access.openOutput(manifest)!!.bufferedWriter().use { it.write("{}") }
      val openedMd = ProjectDocumentOpener(resolver).open(md.accessTreeUri, md.rootDocumentId)
      ProjectCreator(resolver, context.cacheDir).create(
        DocumentsContract.buildTreeDocumentUri(tree.authority, DocumentsContract.getDocumentId(openedMd.sourceDirectoryUri)), "Hidden",
      )
      val repository = BookshelfRepository(context)
      val unavailable = DocumentsContract.buildTreeDocumentUri(tree.authority, "root/missing-${UUID.randomUUID()}")
      assertThrows(Exception::class.java) { runBlocking { repository.scan(unavailable) } }
      val scan = repository.scan(folderTree)
      assertEquals(setOf("Paper", "PDF"), scan.books.map { it.title }.toSet())
      assertEquals(1, scan.warnings.size)
      val paper = scan.books.single { it.title == "Paper" }
      repository.saveAppearance(paper, BookAppearance(title = "Renamed", cover = "paper", paper = "rose"))
      assertEquals("Renamed", repository.scan(folderTree).books.single { it.target == md }.title)
      val pdfBook = scan.books.single { it.target == pdf }
      val mdPosition = ReaderSessionCheckpoint(md.accessTreeUri.toString(), md.rootDocumentId, paper.project.manifest.id,
        0, PdfViewportBookmark.DEFAULT, "PEN")
      val pdfPosition = ReaderSessionCheckpoint(pdf.accessTreeUri.toString(), pdf.rootDocumentId, pdfBook.project.manifest.id,
        1, PdfViewportBookmark(2f, .3f, .7f), "HIGHLIGHTER")
      val positions = BookshelfStore(context)
      positions.saveCheckpoint(mdPosition)
      positions.saveCheckpoint(pdfPosition)
      assertEquals(mdPosition, BookshelfStore(context).checkpoint(md))
      assertEquals(pdfPosition, BookshelfStore(context).checkpoint(pdf))
      val cover = BookCoverLoader(context).load(pdfBook)
      assertEquals(400, cover.width)
      assertTrue(cover.height > cover.width)
      assertTrue(android.graphics.Color.red(cover.getPixel(200, 200)) > 150)
      repository.saveAppearance(pdfBook, BookAppearance(cover = "pdf", page = 1))
      val secondPage = BookCoverLoader(context).load(repository.scan(folderTree).books.single { it.target == pdf })
      assertTrue(Color.blue(secondPage.getPixel(200, 200)) > 150)
      val image = Uri.parse(access.createFile(folder.toString(), "image/png", "custom.png"))
      val pixels = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
      try { resolver.openOutputStream(image)!!.use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) } }
      finally { pixels.recycle() }
      repository.importCover(pdfBook, image, BookAppearance(title = "Local image"))
      DocumentsContract.deleteDocument(resolver, image)
      val customBook = repository.scan(folderTree).books.single { it.target == pdf }
      assertEquals("image", customBook.appearance.cover)
      assertNotNull(customBook.appearance.asset)
      assertTrue(Color.green(BookCoverLoader(context).load(customBook).getPixel(40, 40)) > 200)
      assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveAppearance(pdfBook, BookAppearance(cover = "pdf", page = 8)) } }
      repository.saveAppearance(paper, BookAppearance())
      assertEquals("Paper", repository.scan(folderTree).books.single { it.target == md }.title)
      val sidecar = access.findChild(openedMd.sourceDirectoryUri.toString(), "bookshelf.json")!!
      access.openOutput(sidecar)!!.bufferedWriter().use { it.write("bad json") }
      val fallback = repository.scan(folderTree).books.single { it.target == md }
      assertEquals("Paper", fallback.title)
      assertNotNull(fallback.warning)
      assertTrue(resolver.openInputStream(openedMd.sourceUri)!!.use { it.read() } >= 0)
    } finally { DocumentsContract.deleteDocument(resolver, folder) }
  }
}
