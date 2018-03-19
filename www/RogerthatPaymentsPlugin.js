console.log("Loading RogerthatPaymentsPlugin v0.1.0");

var Utils = require('./Utils');
var PaymentProvider = require('./PaymentProvider');
var PaymentPendingPayment = require('./PaymentPendingPayment');
var UserDetails = require('./UserDetails');
var PaymentAsset = require('./PaymentAsset');
var PaymentTargetInfo = require('./PaymentTargetInfo');
var PaymentTargetInfoAsset = require('./PaymentTargetInfoAsset');

function RogerthatPaymentsPlugin() {
    Utils.logFunctionName("constructor");
}

function createPaymentPendingPayment(transactionId, properties) {
    var ppp = new PaymentPendingPayment();
    ppp["id"] = transactionId;
    Utils.copyProperties(ppp, properties);
    ppp["provider"] = Utils.copyProperties(new PaymentProvider(), properties["provider"]);
    ppp["assets"] = properties.assets.map(function (asset) {
        return Utils.copyProperties(new PaymentAsset(), asset);
    });
    ppp["receiver"] = Utils.copyProperties(new UserDetails(), properties["receiver"]);
    ppp["receiver_asset"] = Utils.copyProperties(new PaymentAsset(), properties["receiver_asset"]);
    return ppp;
}

function createPaymentTargetInfo(targetInfo) {
    var pti = new PaymentTargetInfo();
    if (targetInfo.name !== undefined) {
        Utils.copyProperties(pti, targetInfo);
        pti["assets"] = targetInfo.assets.map(function (asset) {
            return Utils.copyProperties(new PaymentTargetInfoAsset(), asset);
        });
    }
    return pti;
}

RogerthatPaymentsPlugin.prototype.apps = {
    payconiq : {
        install: function(successCallback, errorCallback) {
            Utils.exec(successCallback, errorCallback, "apps_payconiq_install", []);
        },
        installed: function(successCallback, errorCallback, testMode) {
            Utils.exec(successCallback, errorCallback, "apps_payconiq_installed", [{
              test_mode: testMode
            }]);
        },
        pay: function(successCallback, errorCallback, transactionId, testMode) {
            Utils.exec(successCallback, errorCallback, "apps_payconiq_pay", [{
              transaction_id: transactionId,
              test_mode: testMode
            }]);
        }
    }
};

RogerthatPaymentsPlugin.prototype.assets = function (successCallback, errorCallback) {
    var win = function (result) {
        successCallback(result.map(function (asset) {
            return Utils.copyProperties(new PaymentAsset(), asset);
        }));
    };
    Utils.exec(win, errorCallback, "assets", []);
};

RogerthatPaymentsPlugin.prototype.cancelPayment = function (successCallback, errorCallback, transactionId) {
    Utils.exec(successCallback, errorCallback, "cancel_payment", [{transaction_id: transactionId}]);
};

RogerthatPaymentsPlugin.prototype.createTransaction = function (successCallback, errorCallback, updateCallback, providerId, params) {
    var win = function (result) {
        if (result.transaction_id && result.params) {
            successCallback(result);
        } else {
            updateCallback(result);
        }
    };
    Utils.exec(win, errorCallback, "create_transaction", [{provider_id: providerId, params: params}]);
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

RogerthatPaymentsPlugin.prototype.getTargetInfo = function (successCallback, errorCallback, providerId, target, currency) {
    var win = function (result) {
        successCallback(createPaymentTargetInfo(result));
    };
    Utils.exec(win, errorCallback, "get_target_info", [{provider_id: providerId, target: target, currency: currency}]);
};

RogerthatPaymentsPlugin.prototype.providers = function (successCallback, errorCallback, all) {
    var win = function (result) {
        successCallback(result.map(function (provider) {
            return Utils.copyProperties(new PaymentProvider(), provider);
        }));
    };
    Utils.exec(win, errorCallback, "providers", [{all: all || false}]);
};

RogerthatPaymentsPlugin.prototype.getTransactionData = function (successCallback, errorCallback, algorithm, name, index, signature_data) {
    var args = {'key_algorithm': algorithm, 'key_name': name, 'key_index': index, 'signature_data': signature_data};
    Utils.exec(successCallback, errorCallback, 'get_transaction_data', [args]);
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
        callbacks.onProviderUpdated(Utils.copyProperties(new PaymentProvider(), result.args));

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
                return Utils.copyProperties(new PaymentAsset(), asset);
            })
        };
        callbacks.onAssetsUpdated(data);
    } else if (result.callback === "onAssetUpdated") {
        callbacks.onAssetUpdated(Utils.copyProperties(new PaymentAsset(), result.args));
    } else {
        Utils.logFunctionName("processCallbackResult was unhandled");
    }
};

var callbacksRegister = Utils.generateCallbacksRegister(callbacks);
RogerthatPaymentsPlugin.prototype.callbacks = callbacksRegister;

var rogerthatPaymentsPlugin = new RogerthatPaymentsPlugin();
module.exports = rogerthatPaymentsPlugin;

Utils.exec(processCallbackResult, null, "start", []);
