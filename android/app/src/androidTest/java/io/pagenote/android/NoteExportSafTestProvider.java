package io.pagenote.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class NoteExportSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.note-export";

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
    if ("failure".equals(name)) {
      try {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        pipe[0].close();
        return pipe[1];
      } catch (IOException error) {
        FileNotFoundException failure = new FileNotFoundException(error.getMessage());
        failure.initCause(error);
        throw failure;
      }
    }
    if (!"json".equals(name) && !"markdown".equals(name)) {
      throw new FileNotFoundException("unsupported export target");
    }
    File directory = new File(requireContext().getCacheDir(), "note-export-test");
    if (!directory.isDirectory() && !directory.mkdirs()) {
      throw new FileNotFoundException("cannot create export test directory");
    }
    return ParcelFileDescriptor.open(
        new File(directory, name),
        ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public String getType(Uri uri) {
    if ("failure".equals(uri.getLastPathSegment())) return "application/octet-stream";
    return "json".equals(uri.getLastPathSegment()) ? "application/json" : "text/markdown";
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
