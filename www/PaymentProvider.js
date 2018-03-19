var Utils = require('./Utils');
var PaymentProfile = require('./PaymentProfile');
var PaymentAsset = require('./PaymentAsset');

var PaymentProvider = function (id, name, logo_url, version, description, oauth_authorize_url, enabled, asset_types,
                                currencies, black_white_logo, background_color, text_color, button_color) {
    this.id = id || null;
    this.name = name || null;
    this.logo_url = logo_url || null;
    this.version = version || 0;
    this.description = description || null;
    this.oauth_authorize_url = oauth_authorize_url || null;
    this.enabled = enabled || false;
    this.asset_types = asset_types || [];
    this.currencies = currencies || [];
    this.black_white_logo = black_white_logo || null;
    this.background_color = background_color || null;
    this.text_color = text_color || null;
    this.button_color = button_color || null;
};

PaymentProvider.prototype.authorize = function (successCallback, errorCallback) {
    var providerId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else {
        var args = {"oauth_authorize_url": this.oauth_authorize_url};
        Utils.exec(successCallback, errorCallback, "authorize", [args]);
    }
};

PaymentProvider.prototype.profile = function (successCallback, errorCallback) {
    var providerId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else {
        var win = function (result) {
            successCallback(Utils.copyProperties(new PaymentProfile(), result));
        };
        Utils.exec(win, errorCallback, "profile", [{"provider_id": providerId}]);
    }
};

PaymentProvider.prototype.assets = function (successCallback, errorCallback) {
    var providerId = this.id;
    if (providerId === null) {
        errorCallback("providerId was not set");
    } else {
        var win = function (result) {
            var l = [];
            for (var i = 0, len = result.length; i < len; i++) {
                l.push(Utils.copyProperties(new PaymentAsset(), result[i]));
            }
            successCallback(l);
        };
        Utils.exec(win, errorCallback, "assets", [{"provider_id": providerId}]);
    }
};


PaymentProvider.prototype.createAsset = function (successCallback, errorCallback, asset) {
    if (!this.id ) {
        errorCallback("providerId was not set");
    }
    var payload = Object.assign({}, asset, {
        provider_id: this.id
    });
    function success(result){
        successCallback(Utils.copyProperties(new PaymentAsset(), result));
    }
    Utils.exec(success, errorCallback, "create_asset", [payload]);
};

module.exports = PaymentProvider;
