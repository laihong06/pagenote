package io.pagenote.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public final class NoteAssetSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.note-assets";
  public static final String METHOD_RESET = "reset";
  public static final String METHOD_STATS = "stats";
  public static final String METHOD_SET_HIDE_IMAGE_DISPLAY_NAME = "setHideImageDisplayName";
  public static final String EXTRA_HIDE_IMAGE_DISPLAY_NAME = "hideImageDisplayName";
  public static final String EXTRA_FAILURE_MODE = "failureMode";
  public static final String EXTRA_QUERY_COUNT = "queryCount";
  public static final String EXTRA_OPEN_COUNT = "openCount";
  public static final String FAILURE_NONE = "none";
  public static final String FAILURE_QUERY = "query";
  public static final String FAILURE_OPEN = "open";
  public static final String PRIVATE_PROVIDER_DETAIL = "private provider grant token";

  public static final String SOURCE_DOCUMENT_ID = "src";
  public static final String ASSETS_DOCUMENT_ID = "src/assets";
  public static final String ASSETS_NAME = "assets";
  public static final String IMAGE_NAME = "01234567-89ab-cdef-0123-456789abcdef.png";
  public static final String IMAGE_DOCUMENT_ID = ASSETS_DOCUMENT_ID + "/" + IMAGE_NAME;
  public static final String TREE_DOCUMENT_ID = "test-project-root";

  private final AtomicInteger queryCount = new AtomicInteger();
  private final AtomicInteger openCount = new AtomicInteger();
  private volatile String failureMode = FAILURE_NONE;
  private volatile boolean hideImageDisplayName;

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    if (METHOD_RESET.equals(method)) {
      queryCount.set(0);
      openCount.set(0);
      failureMode = extras == null ? FAILURE_NONE : extras.getString(EXTRA_FAILURE_MODE, FAILURE_NONE);
      hideImageDisplayName = false;
      return new Bundle();
    }
    if (METHOD_SET_HIDE_IMAGE_DISPLAY_NAME.equals(method)) {
      hideImageDisplayName = extras != null && extras.getBoolean(EXTRA_HIDE_IMAGE_DISPLAY_NAME, false);
      return new Bundle();
    }
    if (METHOD_STATS.equals(method)) {
      Bundle result = new Bundle();
      result.putInt(EXTRA_QUERY_COUNT, queryCount.get());
      result.putInt(EXTRA_OPEN_COUNT, openCount.get());
      return result;
    }
    return super.call(method, arg, extras);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    queryCount.incrementAndGet();
    String documentId = DocumentsContract.getDocumentId(uri);
    if (FAILURE_QUERY.equals(failureMode) && ASSETS_DOCUMENT_ID.equals(documentId)) {
      throw new SecurityException(PRIVATE_PROVIDER_DETAIL);
    }

    String[] columns = projection == null
        ? new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
          }
        : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    if (IMAGE_DOCUMENT_ID.equals(documentId)) {
      Object[] row = new Object[columns.length];
      for (int index = 0; index < columns.length; index++) {
        if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(columns[index])) {
          row[index] = IMAGE_DOCUMENT_ID;
        } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(columns[index]) ||
            OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
          row[index] = hideImageDisplayName ? null : IMAGE_NAME;
        } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(columns[index])) {
          row[index] = "image/png";
        } else if (OpenableColumns.SIZE.equals(columns[index])) {
          row[index] = fixturePng().length;
        }
      }
      cursor.addRow(row);
      return cursor;
    }
    String childId = null;
    String childName = null;
    if (SOURCE_DOCUMENT_ID.equals(documentId)) {
      childId = ASSETS_DOCUMENT_ID;
      childName = ASSETS_NAME;
    } else if (ASSETS_DOCUMENT_ID.equals(documentId)) {
      childId = IMAGE_DOCUMENT_ID;
      childName = IMAGE_NAME;
    }

    if (childId != null) {
      Object[] row = new Object[columns.length];
      for (int index = 0; index < columns.length; index++) {
        if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(columns[index])) {
          row[index] = childId;
        } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(columns[index])) {
          row[index] = childName;
        }
      }
      cursor.addRow(row);
    }
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    openCount.incrementAndGet();
    if (FAILURE_OPEN.equals(failureMode)) {
      throw new SecurityException(PRIVATE_PROVIDER_DETAIL);
    }
    if (!IMAGE_DOCUMENT_ID.equals(DocumentsContract.getDocumentId(uri))) {
      throw new FileNotFoundException("Unknown fixture document");
    }

    ParcelFileDescriptor[] pipe;
    try {
      pipe = ParcelFileDescriptor.createPipe();
      try (ParcelFileDescriptor.AutoCloseOutputStream output =
               new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
        output.write(fixturePng());
      }
      return pipe[0];
    } catch (IOException exception) {
      throw new FileNotFoundException("Unable to create fixture stream");
    }
  }

  @Override
  public String getType(Uri uri) {
    return "image/png";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  private byte[] fixturePng() {
    Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.MAGENTA);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
        throw new IllegalStateException("Could not encode fixture PNG");
      }
      return output.toByteArray();
    } finally {
      bitmap.recycle();
    }
  }
}
