var exec = require('cordova/exec');

function getErrorCallback (ecb, functionName) {
    if (typeof ecb === 'function') {
        return ecb;
    } else {
        return function (result) {
            console.log("The injected error callback of '" + functionName + "' received: " + JSON.stringify(result));
        };
    }
}

function getSuccessCallback (scb, functionName) {
    if (typeof scb === 'function') {
        return scb;
    } else {
        return function (result) {
            console.log("The injected success callback of '" + functionName + "' received: " + JSON.stringify(result));
        };
    }
}

function _copyProperties (o, properties) {
    for (var i in properties) {
        if (typeof o[i] !== 'undefined' && properties.hasOwnProperty(i)) {
            o[i] = properties[i];
        }
    }
    return o;
}

module.exports = {
    exec : function (success, fail, action, args) {
        if (action !== 'log') {
            console.log("RogerthatPaymentsPlugin.utils.exec -> " + action);
        }
        exec(getSuccessCallback(success, action), getErrorCallback(fail, action), "RogerthatPaymentsPlugin", action, args);
    },
    logFunctionName : function (functionName) {
        console.log("RogerthatPaymentsPlugin." + functionName);
    },
    generateCallbacksRegister : function(callbacks) {
        var callbacksRegister = {};
        Object.keys(callbacks).forEach(function(key) {
            callbacksRegister[key] = function(callback) {
                callbacks[key] = callback;
            };
        });
        return callbacksRegister;
    },
    copyProperties : function (o, properties) {
        return _copyProperties(o, properties);
    },
    createPaymentProvider : function(m, properties) {
        return _copyProperties(m, properties);
    },
    createProfile : function(m, properties) {
        return _copyProperties(m, properties);
    },
    createAsset : function(m, properties) {
        return _copyProperties(m, properties);
    },
    createTransaction : function(m, providerId, assetId, properties) {
        m["provider_id"] = providerId;
        m["asset_id"] = assetId;
        return _copyProperties(m, properties);
    },
    createUserDetails : function(m, properties) {
        return _copyProperties(m, properties);
    }
};
