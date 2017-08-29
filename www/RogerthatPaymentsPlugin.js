console.log("Loading RogerthatPaymentsPlugin v0.1.0");

var Utils = require('./Utils');
var PaymentProvider = require('./PaymentProvider');
var PaymentPendingPayment = require('./PaymentPendingPayment');
var UserDetails = require('./UserDetails');
var PaymentAsset = require('./PaymentAsset');

function RogerthatPaymentsPlugin() {
    Utils.logFunctionName("constructor");
}

function createPaymentPendingPayment(transactionId, properties) {
    var ppp = new PaymentPendingPayment();
    ppp["id"] = transactionId;
    Utils.copyProperties(ppp, properties);
    ppp["provider"] = Utils.createPaymentProvider(new PaymentProvider(), properties["provider"]);
    ppp["assets"] = properties.assets.map(function (asset) {
        return Utils.createAsset(new PaymentAsset(), asset);
    });
    ppp["receiver"] = Utils.createUserDetails(new UserDetails(), properties["receiver"]);
    ppp["receiver_asset"] = Utils.createAsset(new PaymentAsset(), properties["receiver_asset"]);
    return ppp;
}

RogerthatPaymentsPlugin.prototype.providers = function (successCallback, errorCallback, all) {
    var win = function (result) {
        successCallback(result.map(function (provider) {
            return Utils.createPaymentProvider(new PaymentProvider(), provider);
        }));
    };
    Utils.exec(win, errorCallback, "providers", [{all: all || false}]);
};

RogerthatPaymentsPlugin.prototype.assets = function (successCallback, errorCallback) {
    var win = function (result) {
        successCallback(result.map(function (asset) {
            return Utils.createAsset(new PaymentAsset(), asset);
        }));
    };
    Utils.exec(win, errorCallback, "assets", []);
};

RogerthatPaymentsPlugin.prototype.cancelPayment = function (successCallback, errorCallback, transactionId) {
    Utils.exec(successCallback, errorCallback, "cancel_payment", [{transaction_id: transactionId}]);
};

RogerthatPaymentsPlugin.prototype.getPendingPaymentDetails = function (successCallback, errorCallback, updateCallback, transactionId) {
    var win = function (result) {
        if (result.status === "scanned" && result.provider) {
            successCallback(createPaymentPendingPayment(transactionId, result));
        } else {
            updateCallback(result);
        }
    };
    Utils.exec(win, errorCallback, "get_pending_payment_details", [{transaction_id: transactionId}]);
};

var _dummy = function () {
};

var callbacks = {
    onProviderUpdated: _dummy,
    onProviderRemoved: _dummy,
    onAssetsUpdated: _dummy,
    onAssetUpdated: _dummy
};

function processCallbackResult(result) {
    Utils.logFunctionName("processCallbackResult <- " + result.callback);
    console.log(result);
    if (result.callback === "onProviderUpdated") {
        callbacks.onProviderUpdated(Utils.createPaymentProvider(new PaymentProvider(), result.args));

    } else if (result.callback === "onProviderRemoved") {
        // { provider_id: string }
        callbacks.onProviderRemoved(result.args);

    } else if (result.callback === "onAssetsUpdated") {
        /**
         * {
         *   provider_id: string,
         *   assets: Asset[]
         * }
         */
        var data = {
            provider_id: result.args.provider_id,
            assets: result.args.assets.map(function (asset) {
                return Utils.createAsset(new PaymentAsset(), asset);
            })
        };
        callbacks.onAssetsUpdated(data);
    } else if (result.callback === "onAssetUpdated") {
        callbacks.onAssetUpdated(Utils.createAsset(new PaymentAsset(), result.args));
    } else {
        Utils.logFunctionName("processCallbackResult was unhandled");
    }
};

var callbacksRegister = Utils.generateCallbacksRegister(callbacks);
RogerthatPaymentsPlugin.prototype.callbacks = callbacksRegister;

var rogerthatPaymentsPlugin = new RogerthatPaymentsPlugin();
module.exports = rogerthatPaymentsPlugin;

Utils.exec(processCallbackResult, null, "start", []);
