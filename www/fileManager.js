module.exports = {
    choose: function (callbackContext) {
        callbackContext = callbackContext || {};
        cordova.exec(callbackContext.success || null, callbackContext.error || null, "FileManager", "choose", []);
    },
    open: function (fileName, contentType, callbackContext) {
	    callbackContext = callbackContext || {};
	    cordova.exec(callbackContext.success || null, callbackContext.error || null, "FileManager", "open", [fileName, contentType]);
    }
};
