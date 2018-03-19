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

#import "PayconiqHelper.h"

#define PAYCONIQ_SCHEME @"payconiq://"
#define PAYCONIQ_TEST_SCHEME @"payconiq.ext://"
#define PAYCONIQ_APPSTORE_URL @"https://itunes.apple.com/be/app/payconiq/id966172246"


@implementation PayconiqHelper

+ (BOOL)isInstalledWithTestMode:(BOOL)testMode
{
    NSString *scheme = testMode ? PAYCONIQ_TEST_SCHEME : PAYCONIQ_SCHEME;
    return [[UIApplication sharedApplication] canOpenURL:[NSURL URLWithString:scheme]];
}

+ (BOOL)installApp
{
    if ([self isInstalledWithTestMode:NO]) {
        return NO;
    }
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:PAYCONIQ_APPSTORE_URL]];
    return YES;
}

+ (BOOL)startPaymentWithId:(NSString *)transactionId returnUrl:(NSString *)returnUrl testMode:(BOOL)testMode
{
    if (![self isInstalledWithTestMode:testMode]) {
        return NO;
    }
    NSString *scheme = testMode ? PAYCONIQ_TEST_SCHEME : PAYCONIQ_SCHEME;
    NSString *url = [NSString stringWithFormat:@"%@payconiq.com/pay/1/%@?returnUrl=%@", scheme, transactionId, returnUrl];
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:url]];
    return YES;
}

@end
