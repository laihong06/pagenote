package io.pagenote.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public final class NoteImportSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.note-import";
  private static final String BACKUP = "{\"version\":77,\"document\":\"another project\",\"pages\":69," +
      "\"notes\":{\"3\":\"\",\"4\":\"越界\",\"bad\":\"无效\"}}";
  private static final String TEXT = "**导入文本**";
  private static final String INVALID = "{not-json";

  public static Uri uri(String name) {
    return Uri.parse("content://" + AUTHORITY + "/document/" + name);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    String content;
    if ("backup.json".equals(name)) content = BACKUP;
    else if ("notes.md".equals(name)) content = TEXT;
    else if ("invalid.json".equals(name)) content = INVALID;
    else throw new FileNotFoundException("unknown test input");

    File directory = new File(requireContext().getCacheDir(), "note-import-test");
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new FileNotFoundException("cannot create input fixture directory");
    }
    File file = new File(directory, name);
    try (FileOutputStream output = new FileOutputStream(file, false)) {
      output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException error) {
      FileNotFoundException failure = new FileNotFoundException(error.getMessage());
      failure.initCause(error);
      throw failure;
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public String getType(Uri uri) {
    String name = uri.getLastPathSegment();
    if (name != null && name.endsWith(".json")) return "application/json";
    return "text/markdown";
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    return null;
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
}
