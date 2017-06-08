'use strict';

// Start by defining the main module and adding the module dependencies
var app = angular.module(ApplicationConfiguration.applicationModuleName,
    ApplicationConfiguration.applicationModuleVendorDependencies);

// Setting HTML5 Location Mode
angular
    .module(ApplicationConfiguration.applicationModuleName)
    .config(
        [
            '$locationProvider',
            '$translateProvider',
            '$translatePartialLoaderProvider',
            '$httpProvider',
            'AnalyticsProvider',
            function ($locationProvider, $translateProvider,
                      $translatePartialLoaderProvider, $httpProvider, AnalyticsProvider) {
                $locationProvider.hashPrefix('!');

                $httpProvider.interceptors.push(httpInterceptor);

                $httpProvider.interceptors.push(noCacheInterceptor);

                function noCacheInterceptor() {
                    return {
                        request: function (config) {
                            // Appends timestamp to url to avoid
                            // caching issues on IE
                            // only on GET requests with no explicit
                            // cache=true
                            if (config.method == 'GET') {
                                if (!config.cache) {
                                    var separator = config.url
                                        .indexOf('?') === -1 ? '?' : '&';
                                    config.url += separator + 'noCache=' + new Date().getTime();
                                }
                            }
                            return config;
                        }
                    }
                }

                // Initialize angular-translate
                $translateProvider.useLoader('$translatePartialLoader', {
                    urlTemplate: 'api/latest/plugin/admin/labelmanagement/resource?ns={part}&lang={lang}'
                });
                $translateProvider.determinePreferredLanguage(function () {
                    var preferredLocale = "en";

                    //handle incompatible old format; this code will be remove soon after user/developers use this version
                    if (localStorage.AcmLocale) {
                        var lastLocale = angular.fromJson(localStorage.AcmLocale);
                        if (!lastLocale) {
                            localStorage.AcmLocale = null;
                        } else if (!lastLocale.locales) {
                            localStorage.AcmLocale = null;
                        } else if (0 >= lastLocale.locales.length) {
                            localStorage.AcmLocale = null;
                        } else if (lastLocale.locales[0].locale) {
                            localStorage.AcmLocale = null;
                        } else if (!lastLocale.locales[0].native) {
                            localStorage.AcmLocale = null;
                        } else if (!lastLocale.code) {
                            localStorage.AcmLocale = null;
                        } else if (!lastLocale.iso) {
                            localStorage.AcmLocale = null;
                        }
                    }
                    //TODO: remove above block

                    if (localStorage.AcmLocale && "null" != localStorage.AcmLocale) {
                        var lastLocale = angular.fromJson(localStorage.AcmLocale);
                        if (lastLocale && lastLocale.selected) {
                            preferredLocale = lastLocale.selected;
                        }
                    }
                    return preferredLocale;
                });

                // The 'escape' strategy seems to cause failed translation of {{'xxx' | translate}}, and also
                // when texts contain '&'. Disable this until the bug is fixed by Angular.
                //
                //$translateProvider.useSanitizeValueStrategy('escape');


                // Add HTTP error interceptor
                function httpInterceptor($q, $window, $rootScope,
                                         MessageService) {
                    return {
                        responseError: responseError
                    };

                    // Intercept the failed response.
                    function responseError(response) {
                        // Redirect to login page on 401
                        // TODO Should the application caches be
                        // emptied? If the same user logs in no, but
                        // what about for a different user?
                        if (response.status === 401) {
                            // reload the current page to get the
                            // user redirected to the login page and
                            // return to the same page after login
                            // Spring security on the server
                            // remembers the last requested page
                            var redirectUrl = response.headers()['acm_concurrent_session_redirect'];
                            if (redirectUrl) {
                                $window.location.href = redirectUrl;
                                return ($q.reject(null));
                            }
                            sessionStorage.redirectState = angular
                                .toJson($window.location);
                            $window.location.reload();
                            return ($q.reject(null));
                        }

                        if (response.status === 403) {
                            // user is authenticated but tries to modify some entity
                            // with no granted permission
                            MessageService.error('User has no granted permission for this action');
                        }

                        // Send error message to MessageService if
                        // is not suppressed
                        if (isErrorSuppressed(response)) {
                            return $q.reject(response);
                        } else {
                            // Only throw http error as last resort
                            if (response.data) {
                                // e.g. Task already claimed ..
                                // exception type is ...
                                MessageService.error(response.data);
                            } else if (response.statusText) {
                                // e.g. Unknown Error
                                MessageService
                                    .error(response.statusText);
                            } else {
                                // e.g. Error 404 /api/latest..
                                MessageService.httpError(response);
                            }
                            return ($q.reject(response));
                        }
                    }

                    function isErrorSuppressed(response) {
                        // dmiller 2016-04-11 suppressing errors by
                        // default.
                        // TODO: need a configuration flag and/or a
                        // smarter messaging strategy
                        var isSuppressed = true;
                        angular
                            .forEach(
                                ApplicationConfiguration.suppressedErrorList,
                                function (error) {
                                    if (error.url == response.config.url && error.status == response.status) {
                                        isSuppressed = true;
                                    }
                                });
                        return isSuppressed;
                    }
                }

                if (typeof GOOGLE_ANALYTICS_ENABLED === 'undefined') { // sanity check
                    // this means that "api/latest/plugin/admin/googleAnalytics/config.js" couldn't
                    // be generated (very unlikely, but possible) and we are disabling Google Analytics
                    AnalyticsProvider.disableAnalytics(true);
                } else {
                    AnalyticsProvider.disableAnalytics(!GOOGLE_ANALYTICS_ENABLED); // configuration toggle
                    AnalyticsProvider.setAccount(GOOGLE_ANALYTICS_TRACKING_ID); // configuration property
                    AnalyticsProvider.enterDebugMode(GOOGLE_ANALYTICS_DEBUG); // configuration debug flag
                    AnalyticsProvider.setPageEvent('$stateChangeSuccess');
                }
            }
        ]).run(['$translate', '$translatePartialLoader',
            function ($translate, $translatePartialLoader) {
                $translatePartialLoader.addPart('core');
                $translatePartialLoader.addPart('welcome');
                $translate.refresh();
            }
        ]);


angular
    .element(document)
    .ready(function () {
        angular.bootstrap(document, [ApplicationConfiguration.applicationModuleName]);
    });
