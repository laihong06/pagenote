package io.pagenote.android;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExistingProjectSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.existing-project";
  public static final String ROOT_ID = "fixture";
  public static final String SRC_ID = ROOT_ID + "/src";
  public static final String ASSETS_ID = SRC_ID + "/assets";
  public static final String MANIFEST_ID = ROOT_ID + "/project.pagenote";
  public static final String PDF_ID = SRC_ID + "/pattern-recognition.pdf";
  public static final String NOTES_ID = SRC_ID + "/notes.md";
  public static final String REVIEWS_ID = SRC_ID + "/ai-reviews.json";
  public static final String ANNOTATIONS_ID = SRC_ID + "/annotations.json";
  public static final String METHOD_INSTALL = "installFixture";
  public static final String METHOD_REPLACE_NOTES = "replaceNotes";
  public static final String METHOD_GET_REVIEW_WRITE_COUNT = "getReviewWriteCount";
  public static final String METHOD_CONFIGURE_REVIEW_WRITE_FAILURE = "configureReviewWriteFailure";
  public static final String METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE = "configureAnnotationWriteFailure";
  public static final String METHOD_CONFIGURE_CHILD_QUERY_FAILURE = "configureChildQueryFailure";
  public static final String METHOD_GET_CREATE_DOCUMENT_COUNT = "getCreateDocumentCount";
  public static final String METHOD_CONFIGURE_RECOVERY_DELETE_FAILURE = "configureRecoveryDeleteFailure";
  public static final String METHOD_CONFIGURE_IMAGE_WRITE_FAILURE = "configureImageWriteFailure";
  public static final String METHOD_GET_IMAGE_COUNT = "getImageCount";
  public static final String METHOD_WRITE_OVERSIZED_REVIEWS = "writeOversizedReviews";
  public static final String METHOD_PAUSE_NEXT_REVIEW_READ = "pauseNextReviewRead";
  public static final String METHOD_WAIT_REVIEW_SNAPSHOT = "waitReviewSnapshot";
  public static final String METHOD_RELEASE_REVIEW_READ = "releaseReviewRead";
  public static final String METHOD_PAUSE_NEXT_NOTE_WRITE = "pauseNextNoteWrite";
  public static final String METHOD_WAIT_NOTE_WRITE = "waitNoteWrite";
  public static final String METHOD_RELEASE_NOTE_WRITE = "releaseNoteWrite";
  public static final String METHOD_CHECK_NOTE_WRITE = "checkNoteWrite";
  public static final String METHOD_GRANT_PERSISTABLE_READ = "grantPersistableRead";
  private static final String METHOD_FIND_DOCUMENT_PATH = "android:findDocumentPath";
  private static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
  private static final String METHOD_DELETE_DOCUMENT = "android:deleteDocument";
  public static final String EXTRA_URI = "uri";
  private static final String EXTRA_RESULT = "result";
  private static final String EXTRA_MIME_TYPE = "mime_type";
  public static final String EXTRA_MANIFEST = "manifest";
  public static final String EXTRA_PDF = "pdf";
  public static final String EXTRA_NOTES = "notes";
  public static final String EXTRA_REVIEWS = "reviews";
  public static final String EXTRA_OMIT_REVIEWS = "omit_reviews";
  public static final String EXTRA_OMIT_ANNOTATIONS = "omit_annotations";
  public static final String EXTRA_COUNT = "count";
  public static final String EXTRA_FAILURES = "failures";
  public static final String EXTRA_SIZE = "size";
  public static final String EXTRA_PACKAGE_NAME = "package_name";

  private static final String FIXTURE_DIR = "pagenote-existing-project-fixture";
  private static final String MANIFEST_FILE = "project.pagenote";
  private static final String PDF_FILE = "pattern-recognition.pdf";
  private static final String NOTES_FILE = "notes.md";
  private static final String REVIEWS_FILE = "ai-reviews.json";
  private static final String ANNOTATIONS_FILE = "annotations.json";
  private static final String ASSETS_DIRECTORY = "assets";
  private int reviewWriteCount;
  private int reviewWriteFailuresRemaining;
  private int annotationWriteFailuresRemaining;
  private int childQueryFailuresRemaining;
  private int createDocumentCallCount;
  private int recoveryDeleteFailuresRemaining;
  private int imageWriteFailuresRemaining;
  private final Map<String, String> recoveryFiles = new HashMap<>();
  private final Map<String, String> imageFiles = new HashMap<>();
  private boolean assetsDirectoryCreated;
  private static volatile ReviewReadPause reviewReadPause;
  private static volatile NoteWritePause noteWritePause;

  private static final class ReviewReadPause {
    final AtomicBoolean claimed = new AtomicBoolean();
    final CountDownLatch snapshotReady = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
  }

  private static final class NoteWritePause {
    final AtomicBoolean claimed = new AtomicBoolean();
    final CountDownLatch writeStarted = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    try {
      if (METHOD_INSTALL.equals(method)) {
        for (String name : recoveryFiles.values()) new File(fixtureDir(), name).delete();
        recoveryFiles.clear();
      for (String name : imageFiles.values()) new File(fixtureDir(), ASSETS_DIRECTORY + "/" + name).delete();
      imageFiles.clear();
      new File(fixtureDir(), ASSETS_DIRECTORY).delete();
      assetsDirectoryCreated = false;
        writeFixtureFile(MANIFEST_FILE, requiredBytes(extras, EXTRA_MANIFEST));
        writeFixtureFile(PDF_FILE, requiredBytes(extras, EXTRA_PDF));
        writeFixtureFile(NOTES_FILE, requiredBytes(extras, EXTRA_NOTES));
        File reviewsFile = new File(fixtureDir(), REVIEWS_FILE);
        if (extras != null && extras.getBoolean(EXTRA_OMIT_REVIEWS, false)) {
          reviewsFile.delete();
        } else {
          writeFixtureFile(REVIEWS_FILE, requiredBytes(extras, EXTRA_REVIEWS));
        }
        File annotationsFile = new File(fixtureDir(), ANNOTATIONS_FILE);
        if (extras != null && extras.getBoolean(EXTRA_OMIT_ANNOTATIONS, false)) {
          annotationsFile.delete();
        } else {
          writeFixtureFile(ANNOTATIONS_FILE, (
              "{\"document\":{\"2\":[{\"type\":\"pen\",\"color\":\"#112233\",\"width\":2," +
                  "\"points\":[[10,20]],\"pressures\":[1]}]},\"note\":{},\"desktopOnly\":{\"keep\":true}}"
              ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        reviewWriteCount = 0;
        reviewWriteFailuresRemaining = 0;
        annotationWriteFailuresRemaining = 0;
        childQueryFailuresRemaining = 0;
        createDocumentCallCount = 0;
        recoveryDeleteFailuresRemaining = 0;
        imageWriteFailuresRemaining = 0;
        releaseReviewReadPause();
        releaseNoteWritePause();
        return Bundle.EMPTY;
      }
      if (METHOD_GRANT_PERSISTABLE_READ.equals(method)) {
        Uri uri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        String packageName = extras == null ? null : extras.getString(EXTRA_PACKAGE_NAME);
        if (uri == null || packageName == null) {
          throw new IllegalArgumentException("missing URI or package name");
        }
        getContext().grantUriPermission(
            packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return Bundle.EMPTY;
      }
      if (METHOD_REPLACE_NOTES.equals(method)) {
        writeFixtureFile(NOTES_FILE, requiredBytes(extras, EXTRA_NOTES));
        return Bundle.EMPTY;
      }
      if (METHOD_GET_REVIEW_WRITE_COUNT.equals(method)) {
        Bundle result = new Bundle();
        result.putInt(EXTRA_COUNT, reviewWriteCount);
        result.putInt(EXTRA_FAILURES, reviewWriteFailuresRemaining);
        return result;
      }
      if (METHOD_CONFIGURE_REVIEW_WRITE_FAILURE.equals(method)) {
        reviewWriteFailuresRemaining = extras == null ? 0 : extras.getInt(EXTRA_FAILURES);
        return Bundle.EMPTY;
      }
      if (METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE.equals(method)) {
        annotationWriteFailuresRemaining = extras == null ? 0 : extras.getInt(EXTRA_FAILURES);
        return Bundle.EMPTY;
      }
      if (METHOD_CONFIGURE_CHILD_QUERY_FAILURE.equals(method)) {
        childQueryFailuresRemaining = extras == null ? 0 : extras.getInt(EXTRA_FAILURES);
        return Bundle.EMPTY;
      }
      if (METHOD_GET_CREATE_DOCUMENT_COUNT.equals(method)) {
        Bundle result = new Bundle();
        result.putInt(EXTRA_COUNT, createDocumentCallCount);
        return result;
      }
      if (METHOD_CONFIGURE_RECOVERY_DELETE_FAILURE.equals(method)) {
        recoveryDeleteFailuresRemaining = extras == null ? 0 : extras.getInt(EXTRA_FAILURES);
        return Bundle.EMPTY;
      }
      if (METHOD_CONFIGURE_IMAGE_WRITE_FAILURE.equals(method)) {
        imageWriteFailuresRemaining = extras == null ? 0 : extras.getInt(EXTRA_FAILURES);
        return Bundle.EMPTY;
      }
      if (METHOD_GET_IMAGE_COUNT.equals(method)) {
        Bundle result = new Bundle();
        result.putInt(EXTRA_COUNT, imageFiles.size());
        result.putBoolean("assetsDirectoryCreated", assetsDirectoryCreated);
        return result;
      }
      if (METHOD_WRITE_OVERSIZED_REVIEWS.equals(method)) {
        writeOversizedReviews(extras == null ? 0 : extras.getLong(EXTRA_SIZE));
        return Bundle.EMPTY;
      }
      if (METHOD_CREATE_DOCUMENT.equals(method)) {
        createDocumentCallCount++;
        Uri parentUri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (parentUri == null) {
          throw new IllegalArgumentException("missing parent");
        }
        String parentId = DocumentsContract.getDocumentId(parentUri);
        if (!SRC_ID.equals(parentId) && !ASSETS_ID.equals(parentId)) {
          throw new IllegalArgumentException("unsupported parent: " + parentUri);
        }
        String displayName = extras == null
            ? null
            : extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        String mimeType = extras == null ? null : extras.getString(EXTRA_MIME_TYPE);
        if (displayName == null || mimeType == null) {
          throw new IllegalArgumentException(
              "invalid document request: name=" + displayName + " mime=" + mimeType);
        }
        String documentId;
        if (SRC_ID.equals(parentId) && DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)
            && ASSETS_DIRECTORY.equals(displayName)) {
          assetsDirectoryCreated = true;
          if (!new File(fixtureDir(), ASSETS_DIRECTORY).mkdirs()
              && !new File(fixtureDir(), ASSETS_DIRECTORY).isDirectory()) {
            throw new IOException("Could not create assets directory");
          }
          documentId = ASSETS_ID;
        } else if (ASSETS_ID.equals(parentId) && mimeType.startsWith("image/")) {
          documentId = ASSETS_ID + "/" + displayName;
          if (imageFiles.containsKey(documentId)) throw new IllegalArgumentException("duplicate image name");
          File assets = new File(fixtureDir(), ASSETS_DIRECTORY);
          if (!assets.isDirectory() && !assets.mkdirs()) throw new IOException("assets directory unavailable");
          try (FileOutputStream output = new FileOutputStream(new File(assets, displayName), false)) {
            output.flush();
          }
          imageFiles.put(documentId, displayName);
        } else if (SRC_ID.equals(parentId) && ANNOTATIONS_FILE.equals(displayName)) {
          File canonical = new File(fixtureDir(), ANNOTATIONS_FILE);
          if (!canonical.exists()) {
            writeFixtureFile(ANNOTATIONS_FILE, new byte[0]);
            documentId = ANNOTATIONS_ID;
          } else {
            String duplicateName = nextDuplicateAnnotationsName();
            documentId = SRC_ID + "/" + duplicateName;
            writeFixtureFile(duplicateName, new byte[0]);
            recoveryFiles.put(documentId, duplicateName);
          }
        } else if (SRC_ID.equals(parentId)) {
          documentId = SRC_ID + "/" + displayName;
          if (recoveryFiles.containsKey(documentId)) throw new IllegalArgumentException("duplicate recovery name");
          writeFixtureFile(displayName, new byte[0]);
          recoveryFiles.put(documentId, displayName);
        } else {
          throw new IllegalArgumentException("unsupported document request");
        }
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId));
        return result;
      }
      if (METHOD_DELETE_DOCUMENT.equals(method)) {
        if (recoveryDeleteFailuresRemaining > 0) {
          recoveryDeleteFailuresRemaining--;
          throw new IllegalStateException("injected recovery-copy delete failure");
        }
        Uri documentUri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (documentUri == null || !deleteFixtureDocument(DocumentsContract.getDocumentId(documentUri))) {
          throw new FileNotFoundException("recovery copy not found");
        }
        return Bundle.EMPTY;
      }
      if (METHOD_PAUSE_NEXT_REVIEW_READ.equals(method)) {
        reviewReadPause = new ReviewReadPause();
        return Bundle.EMPTY;
      }
      if (METHOD_PAUSE_NEXT_NOTE_WRITE.equals(method)) {
        noteWritePause = new NoteWritePause();
        return Bundle.EMPTY;
      }
      if (METHOD_WAIT_NOTE_WRITE.equals(method)) {
        NoteWritePause pause = noteWritePause;
        boolean started = false;
        if (pause != null) {
          try {
            started = pause.writeStarted.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
          }
        }
        Bundle result = new Bundle();
        result.putBoolean("started", started);
        return result;
      }
      if (METHOD_CHECK_NOTE_WRITE.equals(method)) {
        NoteWritePause pause = noteWritePause;
        Bundle result = new Bundle();
        result.putBoolean("started", pause != null && pause.writeStarted.getCount() == 0);
        return result;
      }
      if (METHOD_RELEASE_NOTE_WRITE.equals(method)) {
        releaseNoteWritePause();
        return Bundle.EMPTY;
      }
      if (METHOD_WAIT_REVIEW_SNAPSHOT.equals(method)) {
        ReviewReadPause pause = reviewReadPause;
        Bundle result = new Bundle();
        boolean ready = false;
        if (pause != null) {
          try {
            ready = pause.snapshotReady.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
          }
        }
        result.putBoolean("ready", ready);
        return result;
      }
      if (METHOD_RELEASE_REVIEW_READ.equals(method)) {
        releaseReviewReadPause();
        return Bundle.EMPTY;
      }
      if (METHOD_FIND_DOCUMENT_PATH.equals(method)) {
        Uri uri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (uri == null) return null;
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_RESULT, findPath(DocumentsContract.getDocumentId(uri)));
        return result;
      }
      return super.call(method, arg, extras);
    } catch (IOException error) {
      throw new IllegalStateException("Unable to prepare SAF fixture", error);
    }
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    try {
      if ("children".equals(uri.getLastPathSegment())) {
        if (childQueryFailuresRemaining > 0) {
          childQueryFailuresRemaining--;
          return null;
        }
        return queryChildDocuments(DocumentsContract.getDocumentId(uri), projection);
      }
      return queryDocument(DocumentsContract.getDocumentId(uri), projection);
    } catch (FileNotFoundException error) {
      return null;
    }
  }

  private Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
    Map<String, Object> values = documentValues(documentId);
    if (values == null) throw new FileNotFoundException(documentId);
    String[] columns = projection == null ? values.keySet().toArray(new String[0]) : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    addRow(cursor, columns, values);
    return cursor;
  }

  private Cursor queryChildDocuments(String parentDocumentId, String[] projection) throws FileNotFoundException {
    List<String> children;
    if (ROOT_ID.equals(parentDocumentId)) {
      children = Arrays.asList(MANIFEST_ID, SRC_ID);
    } else if (SRC_ID.equals(parentDocumentId)) {
      children = new ArrayList<>(Arrays.asList(PDF_ID, NOTES_ID));
      if (new File(fixtureDir(), REVIEWS_FILE).exists()) children.add(REVIEWS_ID);
      if (new File(fixtureDir(), ANNOTATIONS_FILE).exists()) children.add(ANNOTATIONS_ID);
      if (assetsDirectoryCreated) children.add(ASSETS_ID);
      children.addAll(recoveryFiles.keySet());
    } else if (ASSETS_ID.equals(parentDocumentId) && assetsDirectoryCreated) {
      children = new ArrayList<>(imageFiles.keySet());
    } else {
      throw new FileNotFoundException(parentDocumentId);
    }
    String[] columns = projection == null ? new String[] {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
      } : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    for (String child : children) addRow(cursor, columns, documentValues(child));
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    String documentId = DocumentsContract.getDocumentId(uri);
    File file = fileFor(documentId);
    if (file == null || !file.exists()) throw new FileNotFoundException(documentId);
    if (REVIEWS_ID.equals(documentId) && mode.contains("w")) {
      reviewWriteCount++;
      if (reviewWriteFailuresRemaining > 0) {
        reviewWriteFailuresRemaining--;
        failAfterPartialTruncate(file, "review JSON");
      }
    }
    if (ANNOTATIONS_ID.equals(documentId) && mode.contains("w") && annotationWriteFailuresRemaining > 0) {
      annotationWriteFailuresRemaining--;
      failAfterPartialTruncate(file, "annotation JSON");
    }
    if (imageFiles.containsKey(documentId) && mode.contains("w") && imageWriteFailuresRemaining > 0) {
      imageWriteFailuresRemaining--;
      failAfterPartialTruncate(file, "project image");
    }
    if (NOTES_ID.equals(documentId) && mode.contains("w")) {
      NoteWritePause pause = noteWritePause;
      if (pause != null && pause.claimed.compareAndSet(false, true)) {
        pause.writeStarted.countDown();
        try {
          if (!pause.release.await(10, TimeUnit.SECONDS)) {
            throw new FileNotFoundException("timed out waiting to release test note write");
          }
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new FileNotFoundException("interrupted while waiting to release test note write");
        }
      }
    }
    if (REVIEWS_ID.equals(documentId) && !mode.contains("w")) {
      ReviewReadPause pause = reviewReadPause;
      if (pause != null && pause.claimed.compareAndSet(false, true)) {
        return pausedReviewRead(file, pause);
      }
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
  }

  private void failAfterPartialTruncate(File target, String label) throws FileNotFoundException {
    try (FileOutputStream output = new FileOutputStream(target, false)) {
      output.write('[');
      output.flush();
    } catch (IOException error) {
      FileNotFoundException failure = new FileNotFoundException(error.getMessage());
      failure.initCause(error);
      throw failure;
    }
    throw new FileNotFoundException("injected failure after truncating and partially writing " + label);
  }

  private ParcelFileDescriptor pausedReviewRead(File file, ReviewReadPause pause) throws FileNotFoundException {
    try {
      byte[] snapshot = readFile(file);
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
      Thread writer = new Thread(() -> {
        try (ParcelFileDescriptor.AutoCloseOutputStream output =
                 new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
          output.write(snapshot);
          output.flush();
          pause.snapshotReady.countDown();
          pause.release.await(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException error) {
          if (error instanceof InterruptedException) Thread.currentThread().interrupt();
          pause.snapshotReady.countDown();
        }
      }, "saf-test-review-snapshot");
      writer.start();
      return pipe[0];
    } catch (IOException error) {
      throw new FileNotFoundException(error.getMessage());
    }
  }

  private byte[] readFile(File file) throws IOException {
    try (FileInputStream input = new FileInputStream(file)) {
      byte[] buffer = new byte[(int) file.length()];
      int offset = 0;
      while (offset < buffer.length) {
        int count = input.read(buffer, offset, buffer.length - offset);
        if (count < 0) break;
        offset += count;
      }
      return offset == buffer.length ? buffer : Arrays.copyOf(buffer, offset);
    }
  }

  private static void releaseReviewReadPause() {
    ReviewReadPause pause = reviewReadPause;
    if (pause != null) pause.release.countDown();
    reviewReadPause = null;
  }

  private static void releaseNoteWritePause() {
    NoteWritePause pause = noteWritePause;
    if (pause != null) pause.release.countDown();
    noteWritePause = null;
  }

  private void writeOversizedReviews(long size) throws IOException {
    if (size <= 0) throw new IllegalArgumentException("size must be positive");
    byte[] spaces = new byte[8192];
    Arrays.fill(spaces, (byte) ' ');
    try (FileOutputStream output = new FileOutputStream(new File(fixtureDir(), REVIEWS_FILE), false)) {
      output.write('x');
      long remaining = size - 1;
      while (remaining > 0) {
        int count = (int) Math.min(remaining, spaces.length);
        output.write(spaces, 0, count);
        remaining -= count;
      }
    }
  }

  @Override
  public String getType(Uri uri) {
    Map<String, Object> values = documentValues(DocumentsContract.getDocumentId(uri));
    return values == null ? null : (String) values.get(DocumentsContract.Document.COLUMN_MIME_TYPE);
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return deleteFixtureDocument(DocumentsContract.getDocumentId(uri)) ? 1 : 0;
  }

  private boolean deleteFixtureDocument(String documentId) {
    if (ASSETS_ID.equals(documentId)) {
      File directory = new File(fixtureDir(), ASSETS_DIRECTORY);
      if (!directory.delete()) return false;
      assetsDirectoryCreated = false;
      return true;
    }
    String imageName = imageFiles.remove(documentId);
    if (imageName != null) return new File(fixtureDir(), ASSETS_DIRECTORY + "/" + imageName).delete();
    return deleteRecoveryFile(documentId);
  }

  private boolean deleteRecoveryFile(String documentId) {
    String name = recoveryFiles.remove(documentId);
    return name != null && new File(fixtureDir(), name).delete();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  private DocumentsContract.Path findPath(String documentId) throws FileNotFoundException {
    List<String> path;
    if (ROOT_ID.equals(documentId)) {
      path = Collections.singletonList(ROOT_ID);
    } else if (MANIFEST_ID.equals(documentId) || SRC_ID.equals(documentId)) {
      path = Arrays.asList(ROOT_ID, documentId);
    } else if (ASSETS_ID.equals(documentId)) {
      path = Arrays.asList(ROOT_ID, SRC_ID, ASSETS_ID);
    } else if (PDF_ID.equals(documentId) || NOTES_ID.equals(documentId) || REVIEWS_ID.equals(documentId) ||
        ANNOTATIONS_ID.equals(documentId) || recoveryFiles.containsKey(documentId)) {
      path = Arrays.asList(ROOT_ID, SRC_ID, documentId);
    } else if (imageFiles.containsKey(documentId)) {
      path = Arrays.asList(ROOT_ID, SRC_ID, ASSETS_ID, documentId);
    } else {
      throw new FileNotFoundException(documentId);
    }
    return new DocumentsContract.Path(ROOT_ID, path);
  }

  private Map<String, Object> documentValues(String documentId) {
    if (ROOT_ID.equals(documentId)) return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Existing desktop project fixture",
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS, 0);
    if (SRC_ID.equals(documentId)) return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, SRC_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, "src",
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
    if (ASSETS_ID.equals(documentId) && assetsDirectoryCreated) return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, ASSETS_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, ASSETS_DIRECTORY,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
    if (MANIFEST_ID.equals(documentId)) return fileValues(
        MANIFEST_ID, "project.pagenote", "application/json", 0, MANIFEST_FILE);
    if (PDF_ID.equals(documentId)) return fileValues(
        PDF_ID, "pattern-recognition.pdf", "application/pdf", 0, PDF_FILE);
    if (NOTES_ID.equals(documentId)) return fileValues(
        NOTES_ID, "notes.md", "text/markdown", DocumentsContract.Document.FLAG_SUPPORTS_WRITE, NOTES_FILE);
    if (REVIEWS_ID.equals(documentId)) {
      File reviewsFile = new File(fixtureDir(), REVIEWS_FILE);
      return reviewsFile.exists()
          ? fileValues(REVIEWS_ID, "ai-reviews.json", "application/json",
              DocumentsContract.Document.FLAG_SUPPORTS_WRITE, REVIEWS_FILE)
          : null;
    }
    if (ANNOTATIONS_ID.equals(documentId)) {
      File annotationsFile = new File(fixtureDir(), ANNOTATIONS_FILE);
      return annotationsFile.exists()
          ? fileValues(ANNOTATIONS_ID, ANNOTATIONS_FILE, "application/json",
              DocumentsContract.Document.FLAG_SUPPORTS_WRITE, ANNOTATIONS_FILE)
          : null;
    }
    String recoveryName = recoveryFiles.get(documentId);
    if (recoveryName != null) return fileValues(
        documentId, recoveryName, "application/json",
        DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
        recoveryName);
    String imageName = imageFiles.get(documentId);
    if (imageName != null) return fileValues(
        documentId, imageName, mimeForImage(imageName),
        DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
        ASSETS_DIRECTORY + "/" + imageName);
    return null;
  }

  private Map<String, Object> fileValues(
      String id, String name, String mime, int flags, String fileName
  ) {
    File file = new File(fixtureDir(), fileName);
    return values(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, id,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, name,
        DocumentsContract.Document.COLUMN_MIME_TYPE, mime,
        DocumentsContract.Document.COLUMN_FLAGS, flags,
        DocumentsContract.Document.COLUMN_SIZE, file.length(),
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
  }

  private Map<String, Object> values(Object... pairs) {
    Map<String, Object> values = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }

  private void addRow(MatrixCursor cursor, String[] columns, Map<String, Object> values) {
    Object[] row = new Object[columns.length];
    for (int index = 0; index < columns.length; index++) row[index] = values.get(columns[index]);
    cursor.addRow(row);
  }

  private byte[] requiredBytes(Bundle extras, String key) {
    byte[] value = extras == null ? null : extras.getByteArray(key);
    if (value == null) throw new IllegalArgumentException("Missing fixture bytes: " + key);
    return value;
  }

  private void writeFixtureFile(String name, byte[] contents) throws IOException {
    File directory = fixtureDir();
    if (!directory.exists() && !directory.mkdirs()) throw new IOException("Could not create fixture directory");
    try (FileOutputStream output = new FileOutputStream(new File(directory, name), false)) {
      output.write(contents);
    }
  }

  private String nextDuplicateAnnotationsName() {
    int suffix = 1;
    String name;
    do {
      name = "annotations (" + suffix++ + ").json";
    } while (new File(fixtureDir(), name).exists());
    return name;
  }

  private File fixtureDir() {
    return new File(requireContext().getFilesDir(), FIXTURE_DIR);
  }

  private File fileFor(String documentId) {
    if (MANIFEST_ID.equals(documentId)) return new File(fixtureDir(), MANIFEST_FILE);
    if (PDF_ID.equals(documentId)) return new File(fixtureDir(), PDF_FILE);
    if (NOTES_ID.equals(documentId)) return new File(fixtureDir(), NOTES_FILE);
    if (REVIEWS_ID.equals(documentId)) return new File(fixtureDir(), REVIEWS_FILE);
    if (ANNOTATIONS_ID.equals(documentId)) return new File(fixtureDir(), ANNOTATIONS_FILE);
    String recoveryName = recoveryFiles.get(documentId);
    if (recoveryName != null) return new File(fixtureDir(), recoveryName);
    String imageName = imageFiles.get(documentId);
    if (imageName != null) return new File(fixtureDir(), ASSETS_DIRECTORY + "/" + imageName);
    return null;
  }

  private String mimeForImage(String name) {
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
    switch (extension) {
      case "jpg":
      case "jpeg": return "image/jpeg";
      case "gif": return "image/gif";
      case "webp": return "image/webp";
      default: return "image/png";
    }
  }
}
