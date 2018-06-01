package cz.raynet.raynetcrm;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;


public class FileManager extends CordovaPlugin {

    private static final String TAG = "FileManager";
    private static final String ACTION_CHOOSE = "choose";
    private static final String ACTION_OPEN = "open";
    private static final int PICK_FILE_REQUEST = 1;
    private static final String READ_STORAGE_PERMISSION = "android.permission.READ_EXTERNAL_STORAGE";
    private static final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 8;
    CallbackContext callback;

    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {

        try {
            if (action.equals(ACTION_CHOOSE)) {
                chooseFile(callbackContext);
                return true;
            } else if (action.equals(ACTION_OPEN)) {
                openFile(args.getString(0), args.getString(1), callbackContext);
                return true;
            }
        } catch (JSONException e) {
            JSONObject errorObj = new JSONObject();
            errorObj.put("status", PluginResult.Status.JSON_EXCEPTION.ordinal());
            errorObj.put("message", e.getMessage());
            callbackContext.error(errorObj);
        }

        return false;
    }

    private boolean openFile(final String fileName, final String contentType, final CallbackContext callbackContext) throws JSONException {

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        String fileNameTrim = fileName;
        if (fileName.startsWith("file:///")) {
            fileNameTrim = fileName.substring(8);
        } else if (fileName.startsWith("file://")) {
            fileNameTrim = fileName.substring(7);
        }

        File file = new File(fileNameTrim);
        if (file.exists()) {

            try {
                Uri path = Uri.fromFile(file);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(path, contentType);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                cordova.getActivity().startActivity(intent);
                callbackContext.success();

                return true;

            } catch (android.content.ActivityNotFoundException e) {

                JSONObject errorObj = new JSONObject();
                errorObj.put("status", PluginResult.Status.INVALID_ACTION.ordinal());
                errorObj.put("message", "Activity not found: " + e.getMessage());
                callbackContext.error(errorObj);

                return false;
            }

        } else {
            throw new JSONException("File not found");

        }
    }

    private void chooseFile(final CallbackContext callbackContext) {
        callback = callbackContext;
        if (!cordova.hasPermission(READ_STORAGE_PERMISSION)) {
            cordova.requestPermission(this, MY_PERMISSIONS_REQUEST_READ_STORAGE, READ_STORAGE_PERMISSION);
        } else {
            this._chooseFile(callbackContext);
        }
    }

    private void _chooseFile(final CallbackContext callbackContext) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        final Intent chooser = Intent.createChooser(intent, null);
        cordova.startActivityForResult(this, chooser, PICK_FILE_REQUEST);

        final PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for(int r : grantResults) {
            if(r == PackageManager.PERMISSION_DENIED) {
                callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Permission " + READ_STORAGE_PERMISSION + "denied"));
                return;
            }
        }

        switch(requestCode)
        {
            case MY_PERMISSIONS_REQUEST_READ_STORAGE:
                this._chooseFile(callback);
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {

        if (requestCode == PICK_FILE_REQUEST && callback != null) {

            if (resultCode == Activity.RESULT_OK) {

                final Context context = this.cordova.getActivity().getApplicationContext();
                final Uri uri = intent.getData();

                if (uri != null) {
                    try {
                        final String filePath = getFilePath(uri, context);
                        final String fileExtension = getFileExtension(filePath);

                        final JSONObject result = new JSONObject();

                        result.put("uri", uri.toString());
                        result.put("path", filePath);
                        result.put("name", getFileName(filePath));
                        result.put("size", getFileSizeInBytes(uri, context));
                        result.put("type", getMimeType(fileExtension));
                        callback.success(result);
                    } catch (JSONException ex) {
                        callback.error("JSON exception");
                    }
                } else {
                    callback.error("File uri was null");
                }

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // TODO NO_RESULT or error callback?
                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                callback.sendPluginResult(pluginResult);
            } else {
                callback.error(resultCode);
            }
        }
    }

    private int getFileSizeInBytes(final Uri uri, final Context context) {
        int size = 0;
        try {
            ContentResolver cr = context.getContentResolver();
            InputStream is = cr.openInputStream(uri);
            size = is.available();
        } catch (FileNotFoundException ex) { // noop
        } catch (IOException e) { // noop
        }

        return size;
    }

    private String getFileName(final String filePath) {
        if (filePath == null) {
            return null;
        }

        return URLUtil.guessFileName(filePath, null, null);
    }

    private String getFileExtension(final String filePath) {
        if (filePath == null) {
            return null;
        }

        return MimeTypeMap.getFileExtensionFromUrl(filePath);
    }

    private String getMimeType(final String fileExtension) {
        if (fileExtension == null) {
            return null;
        }

        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getMimeTypeFromExtension(fileExtension);
    }

    private String getFilePath(final Uri uri, final Context context) {

        //check here to KITKAT or new version
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    private static String getDataColumn(final Context context, final Uri uri, final String selection, final String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    private static boolean isExternalStorageDocument(final Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(final Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(final Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private static boolean isGooglePhotosUri(final Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
