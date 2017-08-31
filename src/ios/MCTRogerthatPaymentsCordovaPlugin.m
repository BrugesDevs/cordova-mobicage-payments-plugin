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

#import "MBProgressHUD.h"

#import "MCTComponentFramework.h"
#import "MCTHTTPRequest.h"
#import "MCTOauthVC.h"
#import "MCTPaymentPlugin.h"
#import "MCTRogerthatPaymentsCordovaPlugin.h"
#import "MCTSecurity.h"
#import "MCTUINavigationController.h"
#import "MCTUIUtils.h"
#import "MCTUtils.h"


#define PARAMS_REQUIRED() \
if (command.params.count == 0) { \
[self commandProcessedWithMissingArguments:command]; \
return; \
}

#pragma mark - CDVInvokedUrlCommand+Additions

@interface CDVInvokedUrlCommand (MCTInvokedUrlCommandAdditions)

+ (instancetype)commandWithCallbackId:(NSString *)callbackId;
- (NSDictionary *)params;

@end


@implementation CDVInvokedUrlCommand (MCTInvokedUrlCommandAdditions)

+ (instancetype)commandWithCallbackId:(NSString *)callbackId
{
    return [[CDVInvokedUrlCommand alloc] initWithArguments:nil
                                                callbackId:callbackId
                                                 className:nil
                                                methodName:nil];
}

- (NSDictionary *)params
{
    return self.arguments.count ? self.arguments[0] : @{};
}

@end


#pragma mark - MCTRogerthatPaymentsCordovaPlugin

@interface MCTRogerthatPaymentsCordovaPlugin ()

@property (nonatomic, strong) MCTPaymentPlugin *paymentPlugin;
@property (nonatomic, copy) NSString *callbackId;
@property (nonatomic, strong) NSMutableDictionary *callbacks;
@property (nonatomic, strong) NSMutableDictionary *transactionCallbacks;
@property (nonatomic, strong) CDVInvokedUrlCommand *authorizeCallback;
@end

@implementation MCTRogerthatPaymentsCordovaPlugin

- (void)pluginInitialize
{
    [super pluginInitialize];

    self.paymentPlugin = [MCTComponentFramework paymentPlugin];
    self.callbacks = [NSMutableDictionary dictionary];
    self.transactionCallbacks = [NSMutableDictionary dictionary];

    [[MCTComponentFramework intentFramework] registerIntentListener:self
                                                   forIntentActions:@[kINTENT_PAYMENT_PROVIDER_UPDATED,
                                                                      kINTENT_PAYMENT_PROVIDER_REMOVED,
                                                                      kINTENT_PAYMENT_ASSETS_UPDATED,
                                                                      kINTENT_PAYMENT_ASSET_UPDATED,
                                                                      kINTENT_PAYMENT_STATUS_UPDATED,
                                                                      kINTENT_GET_PAYMENT_PROVIDERS_RESULT,
                                                                      kINTENT_GET_PAYMENT_PROVIDERS_FAILED,
                                                                      kINTENT_GET_PAYMENT_PROFILE_RESULT,
                                                                      kINTENT_GET_PAYMENT_PROFILE_FAILED,
                                                                      kINTENT_GET_PAYMENT_ASSETS_RESULT,
                                                                      kINTENT_GET_PAYMENT_ASSETS_FAILED,
                                                                      kINTENT_GET_PAYMENT_TRANSACTIONS_RESULT,
                                                                      kINTENT_GET_PAYMENT_TRANSACTIONS_FAILED,
                                                                      kINTENT_VERIFY_PAYMENT_ASSET_RESULT,
                                                                      kINTENT_VERIFY_PAYMENT_ASSET_FAILED,
                                                                      kINTENT_RECEIVE_PAYMENT_RESULT,
                                                                      kINTENT_RECEIVE_PAYMENT_FAILED,
                                                                      kINTENT_CANCEL_PAYMENT_RESULT,
                                                                      kINTENT_CANCEL_PAYMENT_FAILED,
                                                                      kINTENT_GET_PENDING_PAYMENT_DETAILS_RESULT,
                                                                      kINTENT_GET_PENDING_PAYMENT_DETAILS_FAILED,
                                                                      kINTENT_GET_PENDING_PAYMENT_SIGNATURE_RESULT,
                                                                      kINTENT_GET_PENDING_PAYMENT_SIGNATURE_FAILED,
                                                                      kINTENT_CONFIRM_PAYMENT_RESULT,
                                                                      kINTENT_CONFIRM_PAYMENT_FAILED,
                                                                      kINTENT_CREATE_PAYMENT_ASSET_RESULT,
                                                                      kINTENT_CREATE_PAYMENT_ASSET_FAILED,
                                                                      ]
                                                            onQueue:[MCTComponentFramework mainQueue]];
}

- (void)onIntent:(MCTIntent *)intent
{
    if (self.commandDelegate == nil || self.callbackId == nil) {
        return;
    }

    CDVInvokedUrlCommand *command = nil;

    if ([intent.action isEqualToString:kINTENT_OAUTH_RESULT]) {
        [[MCTComponentFramework intentFramework] unregisterIntentListener:self forIntentAction:kINTENT_OAUTH_RESULT];
        [self loginWithOauthCode:[intent stringForKey:@"code"]
                           state:[intent stringForKey:@"state"]];
    }

    else if ([intent.action isEqualToString:kINTENT_PAYMENT_PROVIDER_UPDATED]) {
        MCT_com_mobicage_to_payment_AppPaymentProviderTO *provider = [intent objectForKey:@"provider"];
        [self sendCallback:@"onProviderUpdated" withArguments:[provider dictRepresentation]];
    }

    else if ([intent.action isEqualToString:kINTENT_PAYMENT_PROVIDER_REMOVED]) {
        NSString *providerId = [intent stringForKey:@"provider_id"];
        [self sendCallback:@"onProviderRemoved" withArguments:@{@"provider_id": OR(providerId, MCTNull)}];
    }

    else if ([intent.action isEqualToString:kINTENT_PAYMENT_ASSETS_UPDATED]) {
        NSString *providerId = [intent stringForKey:@"provider_id"];
        NSArray *assets = [self.paymentPlugin.store paymentAssetsWithProviderId:providerId];
        [self sendCallback:@"onAssetsUpdated" withArguments:@{@"provider_id": OR(providerId, MCTNull),
                                                              @"assets": [self dictArrayFromTOs:assets]}];
    }

    else if ([intent.action isEqualToString:kINTENT_PAYMENT_ASSET_UPDATED]) {
        MCT_com_mobicage_to_payment_PaymentProviderAssetTO *asset = [intent objectForKey:@"asset"];
        [self sendCallback:@"onAssetUpdated" withArguments:[asset dictRepresentation]];
    }

    else if ([intent.action isEqualToString:kINTENT_PAYMENT_STATUS_UPDATED]) {
        MCT_com_mobicage_to_payment_UpdatePaymentStatusRequestTO *item = [intent objectForKey:@"result"];
        if ((command = self.transactionCallbacks[item.transaction_id]) != nil) {
            [self commandProcessed:command withResult:[item dictRepresentation] keepCallback:YES];
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PAYMENT_PROVIDERS_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPaymentProvidersResponseTO *response = [intent objectForKey:@"result"];
            [self commandProcessed:command withResultArray:[self dictArrayFromTOs:response.payment_providers]];
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PAYMENT_PROFILE_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPaymentProfileResponseTO *response = [intent objectForKey:@"result"];
            [self commandProcessed:command withResult:[response dictRepresentation]];
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PAYMENT_ASSETS_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPaymentAssetsResponseTO *response = [intent objectForKey:@"result"];
            [self commandProcessed:command withResultArray:[self dictArrayFromTOs:response.assets]];
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PAYMENT_TRANSACTIONS_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPaymentTransactionsResponseTO *response = [intent objectForKey:@"result"];
            [self commandProcessed:command withResult:@{@"transactions": [self dictArrayFromTOs:response.transactions],
                                                        @"cursor": OR(response.cursor, MCTNull)}];
        }
    }

    else if ([intent.action isEqualToString:kINTENT_VERIFY_PAYMENT_ASSET_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_VerifyPaymentAssetResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                [self commandProcessed:command withResult:nil];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_RECEIVE_PAYMENT_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_ReceivePaymentResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                self.transactionCallbacks[response.result.transaction_id] = command;
                [self commandProcessed:command withResult:[response.result dictRepresentation] keepCallback:YES];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_CANCEL_PAYMENT_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_CancelPaymentResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                [self commandProcessed:command withResult:nil];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PENDING_PAYMENT_DETAILS_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPendingPaymentDetailsResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                self.transactionCallbacks[response.result.transaction_id] = command;
                [self commandProcessed:command withResult:[response.result dictRepresentation] keepCallback:YES];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PENDING_PAYMENT_SIGNATURE_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_GetPendingPaymentSignatureDataResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                NSDictionary *result = [response.result dictRepresentation];
                [self commandProcessed:command withResult:result keepCallback:YES];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_CONFIRM_PAYMENT_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_ConfirmPaymentResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                [self commandProcessed:command withResult:[response.result dictRepresentation]];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_CREATE_PAYMENT_ASSET_RESULT]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            MCT_com_mobicage_to_payment_CreateAssetResponseTO *response = [intent objectForKey:@"result"];
            if (response.success) {
                [self commandProcessed:command withResult:[response.result dictRepresentation]];
            } else {
                [self commandProcessed:command withError:[response.error dictRepresentation]];
            }
        }
    }

    else if ([intent.action isEqualToString:kINTENT_GET_PAYMENT_PROVIDERS_FAILED] ||
             [intent.action isEqualToString:kINTENT_GET_PAYMENT_PROFILE_FAILED] ||
             [intent.action isEqualToString:kINTENT_GET_PAYMENT_ASSETS_FAILED] ||
             [intent.action isEqualToString:kINTENT_GET_PAYMENT_TRANSACTIONS_FAILED] ||
             [intent.action isEqualToString:kINTENT_VERIFY_PAYMENT_ASSET_FAILED] ||
             [intent.action isEqualToString:kINTENT_RECEIVE_PAYMENT_FAILED] ||
             [intent.action isEqualToString:kINTENT_CANCEL_PAYMENT_FAILED] ||
             [intent.action isEqualToString:kINTENT_GET_PENDING_PAYMENT_DETAILS_FAILED] ||
             [intent.action isEqualToString:kINTENT_GET_PENDING_PAYMENT_SIGNATURE_FAILED] ||
             [intent.action isEqualToString:kINTENT_CONFIRM_PAYMENT_FAILED] ||
             [intent.action isEqualToString:kINTENT_CREATE_PAYMENT_ASSET_FAILED]) {
        if ((command = [self popCallbackWithIntent:intent]) != nil) {
            [self commandProcessedWithUnknownError:command];
        }
    }
}

#pragma mark - RogerthatPaymentsPlugin interface

- (void)start:(CDVInvokedUrlCommand *)command
{
    HERE();
    if (self.callbackId != nil) {
        [self commandProcessed:command withErrorString:@"RogerthatPaymentsPlugin already running."];
        return;
    }

    self.callbackId = command.callbackId;
    [self commandProcessed:command];

}

- (void)providers:(CDVInvokedUrlCommand *)command
{
    HERE();
    if ([command.params boolForKey:@"all" withDefaultValue:NO]) {
        [self commandProcessed:command];

        NSString *callbackKey = [self saveCallback:command];
        [self.paymentPlugin requestPaymentProvidersWithCallbackKey:callbackKey];
        return;
    }

    NSArray *providers = [MCTComponentFramework.paymentPlugin.store paymentProviders];
    [self commandProcessed:command withResultArray:[self dictArrayFromTOs:providers]];
}

- (void)authorize:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *oauthAuthorizeUrl = [command.params optObjectForKey:@"oauth_authorize_url"];
    if (oauthAuthorizeUrl == nil) {
        [self commandProcessed:command withErrorString:@"Authorize url not provided"];
        return;
    }

    self.authorizeCallback = command;

    [self commandProcessed:command];

    [[MCTComponentFramework intentFramework] registerIntentListener:self
                                                    forIntentAction:kINTENT_OAUTH_RESULT
                                                            onQueue:[MCTComponentFramework mainQueue]];
    MCTOauthVC *vc = [MCTOauthVC viewControllerWithOauthUrl:[NSURL URLWithString:oauthAuthorizeUrl]];
    MCTUINavigationController *nc = [[MCTUINavigationController alloc] initWithRootViewController:vc];
    vc.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:MCTLocalizedString(@"Back", nil)
                                                                           style:UIBarButtonItemStylePlain
                                                                          target:self
                                                                          action:@selector(authorizeCanceled:)];
    [self.viewController presentViewController:nc
                                      animated:YES
                                    completion:nil];
}

- (void)profile:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *providerId = [command.params optObjectForKey:@"provider_id"];
    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin requestPaymentProfileWithCallbackKey:callbackKey
                                                  providerId:providerId];
}

- (void)assets:(CDVInvokedUrlCommand *)command
{
    HERE();
    NSString *providerId = [command.params optObjectForKey:@"provider_id"];

    NSArray *assets = [self.paymentPlugin.store paymentAssetsWithProviderId:providerId];
    [self commandProcessed:command withResultArray:[self dictArrayFromTOs:assets]];
}

- (void)transactions:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *providerId = [command.params optObjectForKey:@"provider_id"];
    NSString *assetId = [command.params optObjectForKey:@"asset_id"];
    NSString *cursor = [command.params optObjectForKey:@"cursor"];
    NSString *type = [command.params optObjectForKey:@"type"];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin requestPaymentTransactionsWithCallbackKey:callbackKey
                                                       providerId:providerId
                                                          assetId:assetId
                                                           cursor:cursor
                                                             type:type];
}

- (void)verify:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *providerId = [command.params optObjectForKey:@"provider_id"];
    NSString *assetId = [command.params optObjectForKey:@"asset_id"];
    NSString *code = [command.params optObjectForKey:@"code"];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin verifyPaymentAssetWithCallbackKey:callbackKey
                                               providerId:providerId
                                                  assetId:assetId
                                                     code:code];
}

- (void)receive:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *providerId = [command.params optObjectForKey:@"provider_id"];
    NSString *assetId = [command.params optObjectForKey:@"asset_id"];
    NSString *memo = [command.params optObjectForKey:@"memo"];
    MCTlong amount = [command.params longForKey:@"amount" withDefaultValue:0];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin receivePaymentWithCallbackKey:callbackKey
                                           providerId:providerId
                                              assetId:assetId
                                               amount:amount
                                                 memo:memo];
}

- (void)cancel_payment:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *transactionId = [command.params optObjectForKey:@"transaction_id"];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin cancelPaymentWithCallbackKey:callbackKey
                                       transactionId:transactionId];
}

- (void)get_pending_payment_details:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *transactionId = [command.params optObjectForKey:@"transaction_id"];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin requestPendingPaymentDetailsWithCallbackKey:callbackKey
                                                      transactionId:transactionId];
}

- (void)get_pending_payment_signature_data:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *transactionId = [command.params optObjectForKey:@"transaction_id"];
    NSString *assetId = [command.params optObjectForKey:@"asset_id"];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin requestPendingPaymentSignatureDataWithCallbackKey:callbackKey
                                                            transactionId:transactionId
                                                                  assetId:assetId];
}

- (void)confirm_payment:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *transactionId = [command.params optObjectForKey:@"transaction_id"];
    NSString *cryptoTransactionString = [command.params optObjectForKey:@"crypto_transaction"];

    MCT_com_mobicage_to_payment_CryptoTransactionTO *cryptoTransaction;
    if (cryptoTransactionString == nil) {
        cryptoTransaction = nil;
    } else {
        NSDictionary *dict = [cryptoTransactionString MCT_JSONValue];
        cryptoTransaction = [MCT_com_mobicage_to_payment_CryptoTransactionTO transferObjectWithDict:dict];
        if (cryptoTransaction == nil) {
            [self commandProcessed:command
                     withErrorCode:@"parse_error"
                           message:@"Could not parse result json"];
            return;
        }
    }

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin confirmPaymentWithCallbackKey:callbackKey
                                        transactionId:transactionId
                                    cryptoTransaction:cryptoTransaction];
}

- (void)create_asset:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    MCT_com_mobicage_to_payment_CreateAssetRequestTO *request =
    [MCT_com_mobicage_to_payment_CreateAssetRequestTO transferObjectWithDict:command.params];

    [self commandProcessed:command];

    NSString *callbackKey = [self saveCallback:command];
    [self.paymentPlugin createAssetWithCallbackKey:callbackKey
                                           request:request];
}

- (void)get_transaction_data:(CDVInvokedUrlCommand *)command
{
    HERE();
    PARAMS_REQUIRED();

    NSString *keyAlgorithm = [command.params optObjectForKey:@"key_algorithm"];
    NSString *keyName = [command.params optObjectForKey:@"key_name"];
    NSNumber *keyIndex = [command.params optObjectForKey:@"key_index"];
    NSString *signatureData = [command.params optObjectForKey:@"signature_data"];

    MCT_com_mobicage_to_payment_CryptoTransactionTO *cryptoTransaction =
    [MCT_com_mobicage_to_payment_CryptoTransactionTO transferObjectWithDict:[signatureData MCT_JSONValue]];
    if (cryptoTransaction == nil) {
        [self commandProcessed:command
                 withErrorCode:@"parse_error"
                       message:@"Could not parse result json"];
        return;
    }

    NSString *data = [MCTSecurity createTransactionDataWithalgorithm:keyAlgorithm
                                                                name:keyName
                                                               index:keyIndex
                                                   cryptoTransaction:cryptoTransaction];
    [self commandProcessed:command withResult:@{@"data": data}];
}

#pragma mark -

- (void)authorizeCanceled:(id)sender
{
    [self.viewController dismissViewControllerAnimated:YES completion:nil];
}

- (void)loginWithOauthCode:(NSString *)code
                     state:(NSString *)state
{
    HERE();
    MCTUIViewController *vc = [MCTComponentFramework menuViewController];
    UIView *view = vc.navigationController ? vc.navigationController.view : vc.view;
    vc.currentProgressHUD = [[MBProgressHUD alloc] initWithView:view];
    [view addSubview:vc.currentProgressHUD];
    vc.currentProgressHUD.labelText = MCTLocalizedString(@"Processing ...", nil);
    vc.currentProgressHUD.mode = MBProgressHUDModeIndeterminate;
    vc.currentProgressHUD.dimBackground = YES;
    [vc.currentProgressHUD show:YES];

    dispatch_block_t hideProgressHud = ^{
        [vc.currentProgressHUD hide:YES];
        [vc.currentProgressHUD removeFromSuperview];
    };

    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@%@", MCT_HTTPS_BASE_URL, MCT_PAYMENTS_LOGIN_URL]];
    MCTFormDataRequest *request = [[MCTFormDataRequest alloc] initWithURL:url];
    __weak typeof(request) weakHttpRequest = request;
    __weak typeof(self) weakSelf = self;
    request.shouldRedirect = YES;
    request.queuePriority = NSOperationQueuePriorityVeryHigh;

    [request addRequestHeader:@"X-MCTracker-User"
                        value:[[[MCTComponentFramework configProvider] stringForKey:MCT_CONFIGKEY_USERNAME] MCTBase64Encode]];
    [request addRequestHeader:@"X-MCTracker-Pass"
                        value:[[[MCTComponentFramework configProvider] stringForKey:MCT_CONFIGKEY_PASSWORD] MCTBase64Encode]];

    [request setPostValue:code forKey:@"code"];
    [request setPostValue:state forKey:@"state"];
    [request setPostValue:MCT_PRODUCT_ID forKey:@"app_id"];

    [request setFailedBlock:^{
        T_UI();
        hideProgressHud();
        [weakSelf handleLoginResultWithSuccess:NO
                                       message:MCTLocalizedString(@"An unknown error has occurred", nil)];
    }];

    [request setCompletionBlock:^{
        T_UI();
        hideProgressHud();

        int statusCode = weakHttpRequest.responseStatusCode;
        NSDictionary *responseDict = [weakHttpRequest.responseString MCT_JSONValue];
        if (statusCode != 200 || responseDict == nil) {
            NSString *errorMessage = nil;
            if (statusCode == 500) {
                errorMessage = [responseDict optObjectForKey:@"error"];
            }
            if (errorMessage == nil) {
                errorMessage = MCTLocalizedString(@"An unknown error has occurred", nil);
            }
            [weakSelf handleLoginResultWithSuccess:NO message:errorMessage];
            return;
        }

        MCT_com_mobicage_to_payment_AppPaymentProviderTO *provider =
        [MCT_com_mobicage_to_payment_AppPaymentProviderTO transferObjectWithDict:responseDict[@"payment_provider"]];
        [weakSelf.paymentPlugin updatePaymentProvider:provider];
    }];

    [[MCTComponentFramework workQueue] addOperation:request];
}

- (void)handleLoginResultWithSuccess:(BOOL)success
                             message:(NSString *)message
{
    if (success) {
        [self commandProcessed:self.authorizeCallback withResultString:message];
    } else {
        [self commandProcessed:self.authorizeCallback withErrorString:message];
    }
    self.authorizeCallback = nil;
}

#pragma mark -

- (NSString *)saveCallback:(CDVInvokedUrlCommand *)command
{
    NSString *key = [MCTUtils guid];
    self.callbacks[key] = command;
    return key;
}

- (CDVInvokedUrlCommand *)popCallbackWithIntent:(MCTIntent *)intent
{
    return [self popCallbackWithKey:[intent stringForKey:@"callback_key"]];
}

- (CDVInvokedUrlCommand *)popCallbackWithKey:(NSString *)key
{
    CDVInvokedUrlCommand *command = self.callbacks[key];
    if (command) {
        [self.callbacks removeObjectForKey:key];
    }
    return command;
}

#pragma mark -

- (NSArray *)dictArrayFromTOs:(NSArray *)transferObjects
{
    NSMutableArray *dictArray = [NSMutableArray arrayWithCapacity:transferObjects.count];
    for (MCTTransferObject<IJSONable> *transferObject in transferObjects) {
        [dictArray addObject:[transferObject dictRepresentation]];
    }
    return dictArray;
}

#pragma mark -

- (void)commandProcessedWithMissingArguments:(CDVInvokedUrlCommand *)command
{
    [self commandProcessed:command
             withErrorCode:@"arguments_missing"
                   message:@"User did not specify data to encode"];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    pluginResult.keepCallback = @(YES);
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withResult:(NSDictionary *)result
{
    [self commandProcessed:command withResult:result keepCallback:NO];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withResult:(NSDictionary *)result
            keepCallback:(BOOL)keepCallback
{
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                  messageAsDictionary:result];
    pluginResult.keepCallback = @(keepCallback);
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
         withResultArray:(NSArray *)result
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                              messageAsArray:result]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
        withResultString:(NSString *)result
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                             messageAsString:result]
                                callbackId:command.callbackId];
}

- (void)commandProcessedWithUnknownError:(CDVInvokedUrlCommand *)command
{
    [self commandProcessed:command
             withErrorCode:@"unknown_error_occurred"
                   message:MCTLocalizedString(@"An unknown error has occurred", nil)];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
           withErrorCode:(NSString *)code
                 message:(NSString *)message
{
    [self commandProcessed:command withError:@{@"code": code,
                                               @"message": message}];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
               withError:(NSDictionary *)error
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                         messageAsDictionary:error]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
         withErrorString:(NSString *)error
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                             messageAsString:error]
                                callbackId:command.callbackId];
}

- (void)commandProcessed:(CDVInvokedUrlCommand *)command
              withResult:(NSDictionary *)result
               withError:(NSDictionary *)error
{
    if (error) {
        [self commandProcessed:command withError:error];
    } else {
        [self commandProcessed:command withResult:result];
    }
}

- (void)sendCallback:(NSString *)callback withArguments:(id)args
{
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                  messageAsDictionary:@{@"callback": callback,
                                                                        @"args": args}];
    pluginResult.keepCallback = @(YES);
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

@end
