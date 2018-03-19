var PaymentTransaction = function (provider_id, asset_id, id, type, name, amount, currency,
                                   memo, timestamp, from_asset_id, to_asset_id, precision) {
    this.provider_id = provider_id || null;
    this.asset_id = asset_id || null;
    this.id = id || null;
    this.type = type || null;
    this.name = name || null;
    this.amount = amount || 0;
    this.currency = currency || null;
    this.memo = memo || null;
    this.timestamp = timestamp || 0;
    this.from_asset_id = from_asset_id || null;
    this.to_asset_id = to_asset_id || null;
    this.precision = precision || 2;
};

module.exports = PaymentTransaction;
