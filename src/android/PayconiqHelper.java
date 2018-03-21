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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

public class PayconiqHelper {

    private static final String SCHEME = "payconiq";
    private static final String PLAYSTORE_URL = "https://play.google.com/store/apps/details?id=com.payconiq.customers";

    public static boolean isInstalled(Activity activity, boolean testMode) {
        Intent payconiqIntent = getPayconiqIntent("transactionId", "returnUrl://", testMode);
        PackageManager packageManager = activity.getPackageManager();
        return payconiqIntent.resolveActivity(packageManager) != null;
    }

    public static boolean installApp(Activity activity) {
        if (!isInstalled(activity, false)) {
            Intent playStoreIntent = getPayconiqPlayStoreIntent();
            activity.startActivity(playStoreIntent);
            return true;
        }
        return false;
    }

    public static boolean startPayment(Activity activity, String transactionId, String returnUrl, boolean testMode) {
        if (isInstalled(activity, testMode)) {
            Intent payconiqIntent = getPayconiqIntent(transactionId, returnUrl, testMode);
            activity.startActivity(payconiqIntent);
            return true;
        }
        return false;
    }

    private static Intent getPayconiqIntent(String transactionId, String returnUrl, boolean testMode) {
        Uri uri = Uri.parse(String.format("%1$s://payconiq.com/pay/1/%2$s?returnUrl=%3$s", SCHEME, transactionId,
                returnUrl));
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    private static Intent getPayconiqPlayStoreIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(PLAYSTORE_URL));
    }
}
