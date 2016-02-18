/**
 * Created by dragan.simonovski on 2/17/2016.
 */
'use strict';

/**
 * @ngdoc directive
 * @name global.directive:siple-pager
 * @restrict E
 *
 * @description
 *
 * {@link https://github.com/Armedia/ACM3/blob/develop/acm-user-interface/ark-web/src/main/webapp/resources/directives/simple-pager/simple-pager.client.directive.js directives/simple-pager/simple-pager.client.directive.js}
 *
 * The "Simple-Pager" directive add paging functionality like ui-grid pager
 *
 * @param {Object} pagerData object containing pageSizes array, pageSize and totalItems
 * @param {Function} reloadPage, sends command to parrent control that page or pageSize is changed to reload the page
 *
 * @example
 <example>
 <file name="index.html">
 <simple-pager pager-data="pagerData" reload-page="reloadPage"/>
 </file>
 <file name="app.js">
 angular.module('ngAppDemo', []).controller('ngAppDemoController', function($scope, $log) {
            $scope.pagerData = {
                    pageSizes: [10, 20, 30, 40, 50],
                    pageSize: 50,
                    totalItems: 200
                };
            $scope.reloadPage = function (currentPage, pageSize) {

                var promise = callServiceToGetData(currentPage, pageSize);

                promise.then(function (payload) {
                    var tempData = payload.data;
                    $scope.pagerData.totalItems = payload.data.length;
                });
            }
 });
 </file>
 </example>
 */
angular.module('directives').directive('simplePager', function () {
        return {
            restrict: 'E',              //match only element name
            scope: {
                pagerData: '=',            //= : two way binding so that the data can be monitored for changes
                reloadPage: '='
            },

            link: function (scope) {    //dom operations
                scope.currentPage = 1;

                scope.$watchCollection('pagerData.totalItems', function (totalItems, oldValue) {
                    if (totalItems && totalItems != oldValue) {
                        recalculatePagerNumbers();
                    }
                });

                scope.pageChanged = function (currentPage) {
                    if (currentPage) {
                        scope.currentPage = currentPage;
                    }
                    else {
                        scope.currentPage = 1;
                    }
                    scope.reloadPage(scope.currentPage, scope.pagerData.pageSize);
                    recalculatePagerNumbers();
                };

                function recalculatePagerNumbers() {
                    scope.showingLow = (scope.currentPage - 1) * scope.pagerData.pageSize + 1;
                    var max = scope.currentPage * scope.pagerData.pageSize;
                    scope.showingHigh = max < scope.pagerData.totalItems ? max : scope.pagerData.totalItems;
                    var num = parseInt(scope.pagerData.totalItems / scope.pagerData.pageSize);
                    scope.totalPages = scope.pagerData.totalItems % scope.pagerData.pageSize > 0 ? num + 1 : num;
                }
            },
            templateUrl: 'directives/simple-pager/simple-pager.client.view.html'
        };
    }
);