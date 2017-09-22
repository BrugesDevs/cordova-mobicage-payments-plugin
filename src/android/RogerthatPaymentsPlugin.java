/*
 * Copyright 2017 GIG Technology NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @@license_version:1.3@@
 */

package com.mobicage.rogerthat.cordova;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.mobicage.rogerth.at.R;
import com.mobicage.rogerthat.MainService;
import com.mobicage.rogerthat.OauthActivity;
import com.mobicage.rogerthat.plugins.payment.PaymentPlugin;
import com.mobicage.rogerthat.plugins.payment.PaymentStore;
import com.mobicage.rogerthat.util.TextUtils;
import com.mobicage.rogerthat.util.http.HTTPUtil;
import com.mobicage.rogerthat.util.logging.L;
import com.mobicage.rogerthat.util.security.SecurityUtils;
import com.mobicage.rogerthat.util.system.SafeBroadcastReceiver;
import com.mobicage.rogerthat.util.system.SafeRunnable;
import com.mobicage.rogerthat.util.system.T;
import com.mobicage.rogerthat.util.ui.UIUtils;
import com.mobicage.rpc.Credentials;
import com.mobicage.rpc.IncompleteMessageException;
import com.mobicage.rpc.config.CloudConstants;
import com.mobicage.to.payment.AppPaymentProviderTO;
import com.mobicage.to.payment.CancelPaymentResponseTO;
import com.mobicage.to.payment.ConfirmPaymentResponseTO;
import com.mobicage.to.payment.CreateAssetRequestTO;
import com.mobicage.to.payment.CreateAssetResponseTO;
import com.mobicage.to.payment.CryptoTransactionTO;
import com.mobicage.to.payment.GetPaymentAssetsResponseTO;
import com.mobicage.to.payment.GetPaymentProfileResponseTO;
import com.mobicage.to.payment.GetPaymentProvidersResponseTO;
import com.mobicage.to.payment.GetPaymentTransactionsResponseTO;
import com.mobicage.to.payment.GetPendingPaymentDetailsResponseTO;
import com.mobicage.to.payment.GetPendingPaymentSignatureDataResponseTO;
import com.mobicage.to.payment.PaymentProviderAssetTO;
import com.mobicage.to.payment.PaymentProviderTransactionTO;
import com.mobicage.to.payment.ReceivePaymentResponseTO;
import com.mobicage.to.payment.UpdatePaymentStatusRequestTO;
import com.mobicage.to.payment.VerifyPaymentAssetResponseTO;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.jivesoftware.smack.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RogerthatPaymentsPlugin extends CordovaPlugin {

    private static final int START_OAUTH_REQUEST_CODE = 1;
    private static final int HTTP_RETRY_COUNT = 3;
    private static final int HTTP_TIMEOUT = 10000;

    private CordovaActionScreenActivity mActivity = null;
    private HttpClient mHttpClient;

    private Map<String, CallbackContext> mCallbacks = new HashMap<>();
    private Map<String, CallbackContext> mTransactionCallbacks = new HashMap<>();
    private CallbackContext mAuthorizeCallback;

    private CallbackContext mCallbackContext = null;


    // TODO: 23/06/2017 Cleanup duplicated code
    private BroadcastReceiver mBroadcastReceiver = new SafeBroadcastReceiver() {
        @Override
        public String[] onSafeReceive(final Context context, final Intent intent) {
            CallbackContext callbackContext;
            if (mActivity == null) {
                mActivity = (CordovaActionScreenActivity) cordova.getActivity();
            }
            if (PaymentPlugin.PAYMENT_PROVIDER_UPDATED_INTENT.equals(intent.getAction())) {
                if (mCallbackContext == null) {
                    return null;
                }

                String providerId = intent.getStringExtra("provider_id");
                AppPaymentProviderTO pp = mActivity.getPaymentPlugin().getStore().getPaymentProvider(providerId);
                sendCallbackUpdate("onProviderUpdated", new JSONObject(pp.toJSONMap()));

            } else if (PaymentPlugin.PAYMENT_PROVIDER_REMOVED_INTENT.equals(intent.getAction())) {
                if (mCallbackContext == null) {
                    return null;
                }

                String providerId = intent.getStringExtra("provider_id");

                try {
                    JSONObject obj = new JSONObject();
                    obj.put("provider_id", providerId);
                    sendCallbackUpdate("onProviderRemoved", obj);
                } catch (JSONException e) {
                    L.e("JSONException... This should never happen", e);
                }

            } else if (PaymentPlugin.PAYMENT_ASSETS_UPDATED_INTENT.equals(intent.getAction())) {
                if (mCallbackContext == null) {
                    return null;
                }

                String providerId = intent.getStringExtra("provider_id");
                List<PaymentProviderAssetTO> pas = mActivity.getPaymentPlugin().getStore().getPaymentAssets(providerId);
                JSONArray assets = new JSONArray();
                for (PaymentProviderAssetTO pa : pas) {
                    assets.put(new JSONObject(pa.toJSONMap()));
                }

                try {
                    JSONObject callbackResult = new JSONObject();
                    callbackResult.put("provider_id", providerId);
                    callbackResult.put("assets", assets);
                    sendCallbackUpdate("onAssetsUpdated", callbackResult);
                } catch (JSONException e) {
                    L.e("JSONException... This should never happen", e);
                }

            } else if (PaymentPlugin.PAYMENT_ASSET_UPDATED_INTENT.equals(intent.getAction())) {
                if (mCallbackContext == null) {
                    return null;
                }

                String providerId = intent.getStringExtra("provider_id");
                String assetId = intent.getStringExtra("asset_id");
                PaymentProviderAssetTO pa = mActivity.getPaymentPlugin().getStore().getPaymentAsset(providerId, assetId);
                sendCallbackUpdate("onAssetUpdated", new JSONObject(pa.toJSONMap()));

            } else if (PaymentPlugin.UPDATE_RECEIVE_PAYMENT_STATUS_UPDATED_INTENT.equals(intent.getAction())) {
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("result"));
                try {
                    UpdatePaymentStatusRequestTO item = new UpdatePaymentStatusRequestTO(map);
                    callbackContext = mTransactionCallbacks.get(item.transaction_id);
                    if (callbackContext == null) {
                        return null;
                    }

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, new JSONObject(map));
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);


                } catch (IncompleteMessageException e) {
                    L.bug(e);
                    return null;
                }

            } else if (PaymentPlugin.GET_PAYMENT_PROVIDERS_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPaymentProvidersResponseTO response = null;
                try {
                    response = new GetPaymentProvidersResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PAYMENT_PROVIDERS_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }

                JSONArray ja = new JSONArray();
                for (AppPaymentProviderTO pp : response.payment_providers) {
                    ja.put(new JSONObject(pp.toJSONMap()));
                }
                callbackContext.success(ja);

            } else if (PaymentPlugin.GET_PAYMENT_PROVIDERS_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.GET_PAYMENT_PROFILE_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPaymentProfileResponseTO response;
                try {
                    response = new GetPaymentProfileResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PAYMENT_PROFILE_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                callbackContext.success(new JSONObject(response.toJSONMap()));

            } else if (PaymentPlugin.GET_PAYMENT_PROFILE_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.GET_PAYMENT_ASSETS_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPaymentAssetsResponseTO response = null;
                try {
                    response = new GetPaymentAssetsResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PAYMENT_ASSETS_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }

                JSONArray ja = new JSONArray();
                for (PaymentProviderAssetTO ppa : response.assets) {
                    ja.put(new JSONObject(ppa.toJSONMap()));
                }
                callbackContext.success(ja);
            } else if (PaymentPlugin.GET_PAYMENT_ASSETS_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.GET_PAYMENT_TRANSACTIONS_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPaymentTransactionsResponseTO response;
                try {
                    response = new GetPaymentTransactionsResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PAYMENT_TRANSACTIONS_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }

                JSONArray ja = new JSONArray();
                for (PaymentProviderTransactionTO ppt : response.transactions) {
                    ja.put(new JSONObject(ppt.toJSONMap()));
                }

                JSONObject data = new JSONObject();
                try {
                    data.put("transactions", ja);
                    data.put("cursor", response.cursor);
                    callbackContext.success(data);
                } catch (JSONException e) {
                    callbackContext.error("Failed to parse transactions");
                }

            } else if (PaymentPlugin.GET_PAYMENT_TRANSACTIONS_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.VERIFY_PAYMENT_ASSET_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                VerifyPaymentAssetResponseTO response;
                try {
                    response = new VerifyPaymentAssetResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("VERIFY_PAYMENT_ASSET_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    callbackContext.success();
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.VERIFY_PAYMENT_ASSET_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.RECEIVE_PAYMENT_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                ReceivePaymentResponseTO response;
                try {
                    response = new ReceivePaymentResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("RECEIVE_PAYMENT_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    mTransactionCallbacks.put(response.result.transaction_id, callbackContext);

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, new JSONObject(response.result.toJSONMap()));
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.RECEIVE_PAYMENT_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.CANCEL_PAYMENT_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                CancelPaymentResponseTO response;
                try {
                    response = new CancelPaymentResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("CANCEL_PAYMENT_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    callbackContext.success();
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.CANCEL_PAYMENT_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.GET_PENDING_PAYMENT_DETAILS_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPendingPaymentDetailsResponseTO response;
                try {
                    response = new GetPendingPaymentDetailsResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PENDING_PAYMENT_DETAILS_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    mTransactionCallbacks.put(response.result.transaction_id, callbackContext);

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, new JSONObject(response.result.toJSONMap()));
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.GET_PENDING_PAYMENT_DETAILS_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.GET_PENDING_PAYMENT_SIGNATURE_DATA_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                GetPendingPaymentSignatureDataResponseTO response;
                try {
                    response = new GetPendingPaymentSignatureDataResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("GET_PENDING_PAYMENT_SIGNATURE_DATA_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    PluginResult pluginResult;
                    if (response.result == null) {
                        String r = null;
                        pluginResult = new PluginResult(PluginResult.Status.OK, r);
                    } else {
                        JSONObject r = new JSONObject(response.result.toJSONMap());
                        pluginResult = new PluginResult(PluginResult.Status.OK, r);
                    }

                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.GET_PENDING_PAYMENT_SIGNATURE_DATA_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.CONFIRM_PAYMENT_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                ConfirmPaymentResponseTO response;
                try {
                    response = new ConfirmPaymentResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("CONFIRM_PAYMENT_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    callbackContext.success(new JSONObject(response.result.toJSONMap()));
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }

            } else if (PaymentPlugin.CONFIRM_PAYMENT_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);

            } else if (PaymentPlugin.CREATE_PAYMENT_ASSET_RESULT_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;
                Map<String, Object> map = (Map<String, Object>) JSONValue.parse(intent.getStringExtra("json"));
                CreateAssetResponseTO response;
                try {
                    response = new CreateAssetResponseTO(map);
                } catch (IncompleteMessageException e) {
                    L.bug("CREATE_PAYMENT_ASSET_RESULT_INTENT", e);
                    String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                    returnError(callbackContext, "unknown_error_occurred", errorMessage);
                    return null;
                }
                if (response.success) {
                    callbackContext.success(new JSONObject(response.result.toJSONMap()));
                } else {
                    callbackContext.error(new JSONObject(response.error.toJSONMap()));
                }
            } else if (PaymentPlugin.CREATE_PAYMENT_ASSET_FAILED_INTENT.equals(intent.getAction())) {
                if ((callbackContext = getCallbackContext(intent)) == null)
                    return null;

                String errorMessage = mActivity.getString(R.string.unknown_error_occurred);
                returnError(callbackContext, "unknown_error_occurred", errorMessage);
            }
            return null;
        }
    };

    private CallbackContext getCallbackContext(Intent intent) {
        return getCallbackContext(intent.getStringExtra("callback_key"));
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mHttpClient = HTTPUtil.getHttpClient(HTTP_TIMEOUT, HTTP_RETRY_COUNT);

        final IntentFilter intentFilter = new IntentFilter(PaymentPlugin.PAYMENT_PROVIDER_UPDATED_INTENT);
        intentFilter.addAction(PaymentPlugin.PAYMENT_PROVIDER_REMOVED_INTENT);
        intentFilter.addAction(PaymentPlugin.PAYMENT_ASSETS_UPDATED_INTENT);
        intentFilter.addAction(PaymentPlugin.PAYMENT_ASSET_UPDATED_INTENT);
        intentFilter.addAction(PaymentPlugin.UPDATE_RECEIVE_PAYMENT_STATUS_UPDATED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_PROVIDERS_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_PROVIDERS_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_PROFILE_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_PROFILE_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_ASSETS_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_ASSETS_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_TRANSACTIONS_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PAYMENT_TRANSACTIONS_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.VERIFY_PAYMENT_ASSET_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.VERIFY_PAYMENT_ASSET_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.RECEIVE_PAYMENT_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.RECEIVE_PAYMENT_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.CANCEL_PAYMENT_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.CANCEL_PAYMENT_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PENDING_PAYMENT_DETAILS_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PENDING_PAYMENT_DETAILS_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PENDING_PAYMENT_SIGNATURE_DATA_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.GET_PENDING_PAYMENT_SIGNATURE_DATA_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.CONFIRM_PAYMENT_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.CONFIRM_PAYMENT_FAILED_INTENT);
        intentFilter.addAction(PaymentPlugin.CREATE_PAYMENT_ASSET_RESULT_INTENT);
        intentFilter.addAction(PaymentPlugin.CREATE_PAYMENT_ASSET_FAILED_INTENT);
        cordova.getActivity().registerReceiver(mBroadcastReceiver, intentFilter);
    }

    public void onDestroy() {
        cordova.getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
        L.i("RogerthatPaymentsPlugin.execute '" + action + "'");
        if (mActivity == null) {
            mActivity = (CordovaActionScreenActivity) cordova.getActivity();
        }
        mActivity.getMainService().postOnUIHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {
                if (action == null) {
                    callbackContext.error("Cannot execute 'null' action");
                    return;
                }
                if (action.equals("start")) {
                    if (mCallbackContext != null) {
                        callbackContext.error("RogerthatPaymentPlugin already running.");
                        return;
                    }
                    mCallbackContext = callbackContext;

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                    pluginResult.setKeepCallback(true);
                    mCallbackContext.sendPluginResult(pluginResult);


                } else if (action.equals("providers")) {
                    getPaymentProviders(callbackContext, args.optJSONObject(0));

                } else if (action.equals("authorize")) {
                    authorizePaymentProfile(callbackContext, args.optJSONObject(0));

                } else if (action.equals("profile")) {
                    getPaymentProfile(callbackContext, args.optJSONObject(0));

                } else if (action.equals("assets")) {
                    getPaymentAssets(callbackContext, args.optJSONObject(0));

                } else if (action.equals("transactions")) {
                    getPaymentTransactions(callbackContext, args.optJSONObject(0));

                } else if (action.equals("verify")) {
                    verifyPaymentAsset(callbackContext, args.optJSONObject(0));

                } else if (action.equals("receive")) {
                    receivePayment(callbackContext, args.optJSONObject(0));

                } else if (action.equals("cancel_payment")) {
                    cancelPayment(callbackContext, args.optJSONObject(0));

                } else if (action.equals("get_pending_payment_details")) {
                    getPendingPaymentDetails(callbackContext, args.optJSONObject(0));

                }  else if (action.equals("get_pending_payment_signature_data")) {
                    getPendingPaymentSignatureData(callbackContext, args.optJSONObject(0));

                } else if (action.equals("get_transaction_data")) {
                    getTransactionData(callbackContext, args.optJSONObject(0));

                } else if (action.equals("confirm_payment")) {
                    confirmPayPayment(callbackContext, args.optJSONObject(0));

                } else if (action.equals("create_asset")) {
                    createAsset(callbackContext, args.optJSONObject(0));

                } else {
                    L.e("RogerthatPaymentsPlugin.execute did not match '" + action + "'");
                    callbackContext.error("RogerthatPaymentsPlugin doesn't know how to excecute this action.");
                }
            }
        });
        return true;
    }

    private void sendCallbackUpdate(String callback, boolean args) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("callback", callback);
            obj.put("args", args);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
            pluginResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
    }

    private void sendCallbackUpdate(String callback, JSONObject args) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("callback", callback);
            obj.put("args", args);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
            pluginResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
    }

    private void sendCallbackUpdate(String callback, JSONArray args) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("callback", callback);
            obj.put("args", args);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
            pluginResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            L.e("JSONException... This should never happen", e);
        }
    }

    private String saveCallbackContext(CallbackContext callbackContext) {
        String key = UUID.randomUUID().toString();
        mCallbacks.put(key, callbackContext);
        return key;
    }

    public CallbackContext getCallbackContext(String key) {
        T.UI();
        return mCallbacks.remove(key);
    }

    private void returnArgsMissing(final CallbackContext callbackContext) {
        returnError(callbackContext, "arguments_missing", "User did not specify data to encode");
    }

    private void returnError(final CallbackContext callbackContext, final String code, final String message) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("code", code);
            jo.put("message", message);
            callbackContext.error(jo);
        } catch (JSONException e) {
            callbackContext.error("Could not process json...");
        }
    }

    private void getPaymentProviders(final CallbackContext callbackContext, final JSONObject args) {
        if (args != null) {
            if (args.optBoolean("all", false)) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);

                String callbackKey = saveCallbackContext(callbackContext);
                mActivity.getPaymentPlugin().getPaymentProviders(callbackKey);
                return;
            }
        }
        List<AppPaymentProviderTO> pps = mActivity.getPaymentPlugin().getStore().getPaymentProviders();
        JSONArray ja = new JSONArray();
        for (AppPaymentProviderTO pp : pps) {
            ja.put(new JSONObject(pp.toJSONMap()));
        }

        callbackContext.success(ja);
    }

    private void authorizePaymentProfile(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }

        final String oauthAuthorizeUrl = TextUtils.optString(args, "oauth_authorize_url", null);
        if (oauthAuthorizeUrl == null) {
            callbackContext.error("Authorize url not provided");
            return;
        }
        mAuthorizeCallback = callbackContext;

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        mAuthorizeCallback.sendPluginResult(pluginResult);


        Intent intent = new Intent(mActivity, OauthActivity.class);
        intent.putExtra(OauthActivity.OAUTH_URL, oauthAuthorizeUrl);
        intent.putExtra(OauthActivity.BUILD_URL, false);
        intent.putExtra(OauthActivity.ALLOW_BACKPRESS, true);
        this.cordova.startActivityForResult(this, intent, START_OAUTH_REQUEST_CODE);
    }

    private void getPaymentProfile(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String providerId = TextUtils.optString(args, "provider_id", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().getPaymentProfile(callbackKey, providerId);
    }

    private void getPaymentAssets(final CallbackContext callbackContext, final JSONObject args) {
        final String providerId;
        if (args == null) {
            providerId = null;
        } else {
            providerId = TextUtils.optString(args, "provider_id", null);
        }
        List<PaymentProviderAssetTO> pas = mActivity.getPaymentPlugin().getStore().getPaymentAssets(providerId);
        JSONArray ja = new JSONArray();
        for (PaymentProviderAssetTO pa : pas) {
            ja.put(new JSONObject(pa.toJSONMap()));
        }
        callbackContext.success(ja);
    }

    private void getPaymentTransactions(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String providerId = TextUtils.optString(args, "provider_id", null);
        final String assetId = TextUtils.optString(args, "asset_id", null);
        final String cursor = TextUtils.optString(args, "cursor", null);
        final String type = TextUtils.optString(args, "type", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().getPaymentTransactions(callbackKey, providerId, assetId, cursor, type);
    }

    private void verifyPaymentAsset(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String providerId = TextUtils.optString(args, "provider_id", null);
        final String assetId = TextUtils.optString(args, "asset_id", null);
        final String code = TextUtils.optString(args, "code", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().verifyPaymentAsset(callbackKey, providerId, assetId, code);
    }

    private void receivePayment(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String providerId = TextUtils.optString(args, "provider_id", null);
        final String assetId = TextUtils.optString(args, "asset_id", null);
        final long amount = args.optLong("amount", 0);
        final String memo = TextUtils.optString(args, "memo", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().receivePayment(callbackKey, providerId, assetId, amount, memo);
    }

    private void cancelPayment(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String transactionId = TextUtils.optString(args, "transaction_id", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().cancelPayment(callbackKey, transactionId);
    }

    private void getPendingPaymentDetails(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String transactionId = TextUtils.optString(args, "transaction_id", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().getPendingPaymentDetails(callbackKey, transactionId);
    }

    private void getPendingPaymentSignatureData(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String transactionId = TextUtils.optString(args, "transaction_id", null);
        final String assetId = TextUtils.optString(args, "asset_id", null);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().getPendingPaymentSignatureData(callbackKey, transactionId, assetId);
    }

    private void confirmPayPayment(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        final String transactionId = TextUtils.optString(args, "transaction_id", null);
        final String cryptoTransactionString = TextUtils.optString(args, "crypto_transaction", null);
        CryptoTransactionTO cryptoTransaction;
        if (cryptoTransactionString == null) {
            cryptoTransaction = null;
        } else {
            Map<String, Object> map = (Map<String, Object>) JSONValue.parse(cryptoTransactionString);
            try {
                cryptoTransaction = new CryptoTransactionTO(map);
            } catch (IncompleteMessageException e) {
                L.d("IncompleteMessageException", e);
                returnError(callbackContext, "parse_error", "Could not parse result json");
                return;
            }
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().confirmPayPayment(callbackKey, transactionId, cryptoTransaction);
    }

    private void createAsset(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }
        CreateAssetRequestTO asset = new CreateAssetRequestTO();
        asset.provider_id = TextUtils.optString(args, "provider_id", null);
        asset.type = TextUtils.optString(args, "type", null);
        asset.currency = TextUtils.optString(args, "currency", null);
        asset.iban = TextUtils.optString(args, "iban", null);
        asset.address = TextUtils.optString(args, "address", null);
        asset.id = TextUtils.optString(args, "id", null);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);

        String callbackKey = saveCallbackContext(callbackContext);
        mActivity.getPaymentPlugin().createAsset(callbackKey, asset);
    }

    private void getTransactionData(final CallbackContext callbackContext, final JSONObject args) {
        if (args == null) {
            returnArgsMissing(callbackContext);
            return;
        }

        try {
            final String keyAlgorithm = TextUtils.optString(args, "key_algorithm", null);
            final String keyName = TextUtils.optString(args, "key_name", null);
            final Long keyIndex = TextUtils.optLong(args, "key_index");
            final String signatureData = TextUtils.optString(args, "signature_data", null);

            Map<String, Object> map = (Map<String, Object>) JSONValue.parse(signatureData);
            String data = SecurityUtils.createTransactionData(mActivity.getMainService(), keyAlgorithm, keyName, keyIndex, new CryptoTransactionTO(map));

            JSONObject obj = new JSONObject();
            obj.put("data", data);
            callbackContext.success(obj);

        } catch (Exception e) {
            L.d(e);
            returnError(callbackContext, "parse_error", "Could not parse data");
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        L.i("RogerthatPaymentsPlugin.onActivityResult requestCode -> " + requestCode);
        if (requestCode == START_OAUTH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (!TextUtils.isEmptyOrWhitespace(data.getStringExtra(OauthActivity.RESULT_CODE))) {
                    loginWithOauthCode(data.getStringExtra(OauthActivity.RESULT_CODE), data.getStringExtra(OauthActivity.RESULT_STATE));
                } else {
                    String message = data.getStringExtra(OauthActivity.RESULT_ERROR_MESSAGE);
                    mAuthorizeCallback.error(message);
                    mAuthorizeCallback = null;
                }
            } else {
                mAuthorizeCallback.error("User cancelled");
                mAuthorizeCallback = null;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void runOnUI(SafeRunnable runnable) {
        mActivity.getMainService().postOnUIHandler(runnable);
    }

    private ProgressDialog showProgressDialog(String message) {
        return UIUtils.showProgressDialog(mActivity, null, message, true, false);
    }

    private void handleLoginResult(final ProgressDialog progressDialog, final String errorMessage) {
        handleLoginResult(progressDialog, errorMessage, false);
    }

    private void handleLoginResult(final ProgressDialog progressDialog, final String message, final boolean success) {
        progressDialog.dismiss();
        if (success) {
            mAuthorizeCallback.success(message);
        } else {
            mAuthorizeCallback.error(message);
        }
        mAuthorizeCallback = null;
    }

    private void loginWithOauthCode(final String code, final String state) {
        // Make call to Rogerthat
        final ProgressDialog progressDialog = showProgressDialog(mActivity.getString(R.string.loading));

        mActivity.getMainService().postOnBIZZHandler(new SafeRunnable() {
            @Override
            protected void safeRun() throws Exception {
                HttpPost httpPost = new HttpPost(CloudConstants.HTTPS_BASE_URL + "/payments/login/app"); // todo ruben

                httpPost.setHeader("User-Agent", MainService.getUserAgent(mActivity.getMainService()));
                Credentials credentials = null;
                try {
                    credentials = mActivity.getMainService().getCredentials();
                } catch (Exception e) {
                    L.d("Could not load credentials", e);
                }
                if (credentials != null) {
                    httpPost.setHeader("X-MCTracker-User", Base64.encodeBytes(credentials.getUsername().getBytes(), Base64.DONT_BREAK_LINES));
                    httpPost.setHeader("X-MCTracker-Pass", Base64.encodeBytes(credentials.getPassword().getBytes(), Base64.DONT_BREAK_LINES));
                }

                try {
                    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(8);
                    nameValuePairs.add(new BasicNameValuePair("code", code));
                    nameValuePairs.add(new BasicNameValuePair("state", state));
                    nameValuePairs.add(new BasicNameValuePair("app_id", CloudConstants.APP_ID));
                    httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                    // Execute HTTP Post Request
                    HttpResponse response = mHttpClient.execute(httpPost);

                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();

                    if (entity == null) {
                        handleLoginResult(progressDialog, mActivity.getString(R.string.error_please_try_again));
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    final Map<String, Object> responseMap = (Map<String, Object>) JSONValue
                            .parse(new InputStreamReader(entity.getContent()));

                    if (statusCode != 200 || responseMap == null) {
                        if (statusCode == 500 && responseMap != null) {
                            final String errorMessage = (String) responseMap.get("error");
                            if (errorMessage != null) {
                                runOnUI(new SafeRunnable() {
                                    @Override
                                    protected void safeRun() throws Exception {
                                        T.UI();
                                        handleLoginResult(progressDialog, errorMessage);
                                    }
                                });
                                return;
                            }
                        }
                        handleLoginResult(progressDialog, mActivity.getString(R.string.error_please_try_again));
                        return;
                    }
                    org.json.simple.JSONObject paymentProvider = (org.json.simple.JSONObject) responseMap.get("payment_provider");
                    AppPaymentProviderTO app = new AppPaymentProviderTO(paymentProvider);

                    mActivity.getPaymentPlugin().updatePaymentProvider(app);
                    handleLoginResult(progressDialog, "Authorize success", true);

                } catch (Exception e) {
                    L.d(e);
                    handleLoginResult(progressDialog, mActivity.getString(R.string.error_please_try_again));
                }
            }
        });
    }
}
