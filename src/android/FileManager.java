package cz.raynet.raynetcrm;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;


public class FileManager extends CordovaPlugin {

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

    private void openFile(final String fileName, final String contentType, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FileManager.this._openFile(fileName, contentType, callbackContext);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void _openFile(final String fileArg, final String contentType, final CallbackContext callbackContext) throws JSONException {

        String fileName;
        try {
            CordovaResourceApi resourceApi = webView.getResourceApi();
            Uri fileUri = resourceApi.remapUri(Uri.parse(fileArg));
            fileName = fileUri.getPath();
        } catch (Exception e) {
            fileName = fileArg;
        }

        final File file = new File(fileName);
        if (file.exists()) {
            try {
                final Intent intent = FileUtils.getViewIntent(this.cordova.getActivity().getApplicationContext(), file, contentType);
                cordova.getActivity().startActivity(intent);

                callbackContext.success();
            } catch (android.content.ActivityNotFoundException e) {
                JSONObject errorObj = new JSONObject();
                errorObj.put("status", PluginResult.Status.ERROR.ordinal());
                errorObj.put("message", "Activity not found: " + e.getMessage());
                callbackContext.error(errorObj);
            }
        } else {
            JSONObject errorObj = new JSONObject();
            errorObj.put("status", PluginResult.Status.ERROR.ordinal());
            errorObj.put("message", "File not found");
            callbackContext.error(errorObj);
        }
    }

    private void chooseFile(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    callback = callbackContext;
                    if (!cordova.hasPermission(READ_STORAGE_PERMISSION)) {
                        cordova.requestPermission(FileManager.this, MY_PERMISSIONS_REQUEST_READ_STORAGE, READ_STORAGE_PERMISSION);
                    } else {
                        FileManager.this._chooseFile(callbackContext);
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
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
                        final String filePath = FileUtils.getPath(context, uri);
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
}
