var Utils = require('./Utils');

var PaymentPendingPayment = function (id, provider, assets, receiver, receiver_asset, currency, amount, memo, timestamp) {
    this.id = id || null;
    this.provider = provider || null;
    this.assets = assets || [];
    this.receiver = receiver || null;
    this.receiver_asset = receiver_asset || null;
    this.currency = currency || null;
    this.amount = amount || 0;
    this.memo = memo || null;
    this.timestamp = timestamp || 0;
};

PaymentPendingPayment.prototype.cancel = function(successCallback, errorCallback) {
    var transactionId = this.id;
    if (transactionId === null) {
        errorCallback("transactionId was not set");
    } else {
        var args = {"transaction_id": transactionId};
        Utils.exec(successCallback, errorCallback, "cancel_payment", [args]);
    }
};

PaymentPendingPayment.prototype.getSignatureData = function(successCallback, errorCallback, assetId) {
    var transactionId = this.id;
    if (transactionId === null) {
        errorCallback("transactionId was not set");
    } else {
        var args = {"transaction_id": transactionId, "asset_id": assetId};
        Utils.exec(successCallback, errorCallback, "get_pending_payment_signature_data", [args]);
    }
};

PaymentPendingPayment.prototype.getTransactionData = function(successCallback, errorCallback, algorithm, name, index, signature_data) {
    var transactionId = this.id;
    if (transactionId === null) {
        errorCallback("transactionId was not set");
    } else {
        var args = {"key_algorithm": algorithm, "key_name": name, "key_index": index, "signature_data": signature_data};
        Utils.exec(successCallback, errorCallback, "get_transaction_data", [args]);
    }
};

PaymentPendingPayment.prototype.confirm = function(successCallback, errorCallback, cryptoTransaction) {
    var transactionId = this.id;
    if (transactionId === null) {
        errorCallback("transactionId was not set");
    } else {
        var args = {"transaction_id": transactionId, "crypto_transaction": cryptoTransaction};
        Utils.exec(successCallback, errorCallback, "confirm_payment", [args]);
    }
};

module.exports = PaymentPendingPayment;
