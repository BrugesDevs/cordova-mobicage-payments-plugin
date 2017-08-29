var Utils = require('./Utils');
var PaymentTransaction = require('./PaymentTransaction');

var PaymentAsset = function (provider_id, id, type, name, currency, available_balance, total_balance,
                             verified, enabled, has_balance, has_transactions, required_action) {
    this.provider_id = provider_id || null;
    this.id = id || null;
    this.type = type || null;
    this.name = name || null;
    this.currency = currency || null;
    this.available_balance = available_balance || null;
    this.total_balance = total_balance || null;
    this.verified = verified || false;
    this.enabled = enabled || false;
    this.has_balance = has_balance || false;
    this.has_transactions = has_transactions || false;
    this.required_action = required_action || null;
};

PaymentAsset.prototype.transactions = function (successCallback, errorCallback, cursor, type) {
    var providerId = this.provider_id;
    var assetId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else if (assetId === null) {
        errorCallback("assetId was not set");
    } else if (type === null) {
        errorCallback("type was not set");
    } else {
        var win = function (result) {
            var l = [];
            for (var i = 0, len = result.transactions.length; i < len; i++) {
                l.push(Utils.createTransaction(new PaymentTransaction(), providerId, assetId, result.transactions[i]));
            }
            successCallback({"cursor": result.cursor || null, "transactions": l});
        };
        var args = {"provider_id": providerId, "asset_id": assetId, "cursor": cursor || null, "type": type};
        Utils.exec(win, errorCallback, "transactions", [args]);
    }
};

PaymentAsset.prototype.verify = function (successCallback, errorCallback, code) {
    var providerId = this.provider_id;
    var assetId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else if (assetId === null) {
        errorCallback("assetId was not set");
    } else if (this.verified) {
        errorCallback("No verification needed");
    } else {
        var args = {"provider_id": providerId, "asset_id": assetId, "code": code};
        Utils.exec(successCallback, errorCallback, "verify", [args]);
    }
};

PaymentAsset.prototype.receive = function (successCallback, errorCallback, amount, memo) {
    var providerId = this.provider_id;
    var assetId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else if (assetId === null) {
        errorCallback("assetId was not set");
    } else if (!this.verified) {
        errorCallback("Verification needed");
    } else {
        var args = {"provider_id": providerId, "asset_id": assetId, "amount": amount, "memo": memo};
        Utils.exec(successCallback, errorCallback, "receive", [args]);
    }
};

module.exports = PaymentAsset;
