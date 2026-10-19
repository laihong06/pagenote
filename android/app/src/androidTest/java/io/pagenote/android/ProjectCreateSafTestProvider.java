package io.pagenote.android;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public final class ProjectCreateSafTestProvider extends ContentProvider {
  public static final String AUTHORITY = "io.pagenote.android.test.project-create";
  public static final String ROOT_ID = "root";
  private static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
  private static final String METHOD_DELETE_DOCUMENT = "android:deleteDocument";
  private static final String EXTRA_URI = "uri";
  private static final String EXTRA_MIME_TYPE = "mime_type";
  private static final String[] COLUMNS = {
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      DocumentsContract.Document.COLUMN_FLAGS,
  };

  public static Uri treeUri() {
    return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID);
  }

  public static Uri createSource(ContentResolver resolver, String name, byte[] bytes)
      throws IOException {
    Uri root = DocumentsContract.buildDocumentUriUsingTree(treeUri(), ROOT_ID);
    Uri source = DocumentsContract.createDocument(resolver, root, "application/pdf", name);
    if (source == null) throw new IOException("could not create PDF source fixture");
    try (OutputStream output = resolver.openOutputStream(source, "wt")) {
      if (output == null) throw new IOException("could not open PDF source fixture");
      output.write(bytes);
    }
    return source;
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    try {
      if (METHOD_CREATE_DOCUMENT.equals(method)) {
        Uri parentUri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (parentUri == null) throw new IllegalArgumentException("missing parent URI");
        String parentId = DocumentsContract.getDocumentId(parentUri);
        String displayName = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        String mimeType = extras.getString(EXTRA_MIME_TYPE);
        if (mimeType == null) mimeType = arg;
        if (displayName == null || mimeType == null) {
          throw new IllegalArgumentException("missing document metadata");
        }
        File parent = fileForId(parentId);
        if (!parent.isDirectory()) throw new IOException("parent is not a directory");
        File child = new File(parent, displayName);
        boolean created = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)
            ? child.mkdir()
            : child.createNewFile();
        if (!created) throw new IOException("fixture document already exists");
        String documentId = parentId + "/" + displayName;
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId));
        return result;
      }
      if (METHOD_DELETE_DOCUMENT.equals(method)) {
        Uri documentUri = extras == null ? null : extras.getParcelable(EXTRA_URI);
        if (documentUri == null) throw new IllegalArgumentException("missing document URI");
        if (!deleteRecursively(fileForId(DocumentsContract.getDocumentId(documentUri)))) {
          throw new IOException("could not delete fixture document");
        }
        return Bundle.EMPTY;
      }
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
    return super.call(method, arg, extras);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
      String sortOrder) {
    try {
      String documentId = DocumentsContract.getDocumentId(uri);
      if (uri.getLastPathSegment() != null && "children".equals(uri.getLastPathSegment())) {
        return queryChildren(documentId);
      }
      MatrixCursor cursor = new MatrixCursor(COLUMNS);
      File document = fileForId(documentId);
      if (document.exists()) addRow(cursor, documentId, document);
      return cursor;
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private Cursor queryChildren(String parentId) throws IOException {
    MatrixCursor cursor = new MatrixCursor(COLUMNS);
    File parent = fileForId(parentId);
    File[] children = parent.listFiles();
    if (children == null) return cursor;
    for (File child : children) addRow(cursor, parentId + "/" + child.getName(), child);
    return cursor;
  }

  private void addRow(MatrixCursor cursor, String id, File file) {
    String mimeType = mimeType(file);
    int flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
    if (file.isDirectory()) flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
    else flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
    cursor.newRow()
        .add(id)
        .add(file.getName())
        .add(mimeType)
        .add(file.isFile() ? file.length() : null)
        .add(file.lastModified())
        .add(flags);
  }

  private String mimeType(File file) {
    if (file.isDirectory()) return DocumentsContract.Document.MIME_TYPE_DIR;
    String name = file.getName();
    if (name.endsWith(".pdf")) return "application/pdf";
    if (name.endsWith(".md")) return "text/markdown";
    if ("project.pagenote".equals(name)) return "application/vnd.pagenote.project";
    return "application/octet-stream";
  }

  @Override
  public String getType(Uri uri) {
    try {
      return mimeType(fileForId(DocumentsContract.getDocumentId(uri)));
    } catch (IOException error) {
      return null;
    }
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    try {
      return ParcelFileDescriptor.open(fileForId(DocumentsContract.getDocumentId(uri)),
          ParcelFileDescriptor.parseMode(mode));
    } catch (IOException error) {
      FileNotFoundException failure = new FileNotFoundException(error.getMessage());
      failure.initCause(error);
      throw failure;
    }
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

  private File fileForId(String documentId) throws IOException {
    File root = new File(requireContext().getCacheDir(), "project-create-saf-test");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("fixture root unavailable");
    String relative;
    if (ROOT_ID.equals(documentId)) relative = "";
    else if (documentId.startsWith(ROOT_ID + "/")) relative = documentId.substring(ROOT_ID.length() + 1);
    else throw new IOException("unknown fixture document");
    File canonicalRoot = root.getCanonicalFile();
    File file = new File(canonicalRoot, relative).getCanonicalFile();
    if (!file.toPath().startsWith(canonicalRoot.toPath())) throw new IOException("fixture path escaped root");
    return file;
  }

  private boolean deleteRecursively(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) if (!deleteRecursively(child)) return false;
    }
    return file.delete();
  }
}
