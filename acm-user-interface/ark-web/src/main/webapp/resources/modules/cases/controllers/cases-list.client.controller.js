'use strict';

angular.module('cases').controller('CasesListController', ['$scope', '$state', '$stateParams', '$translate', 'UtilService', 'ConstantService', 'CallCasesService', 'CallConfigService',
    function ($scope, $state, $stateParams, $translate, Util, Constant, CallCasesService, CallConfigService) {
        CallConfigService.getModuleConfig("cases").then(function (config) {
            $scope.treeConfig = config.tree;
            $scope.componentsConfig = config.components;
            return config;
        });


        var firstLoad = true;
        $scope.onLoad = function (start, n, sort, filters) {
            if (firstLoad && $stateParams.id) {
                $scope.treeData = null;
            }

            CallCasesService.queryCasesTreeData(start, n, sort, filters).then(
                function (treeData) {
                    if (firstLoad) {
                        if ($stateParams.id) {
                            if ($scope.treeData) {            //It must be set by CallCasesService.getCaseInfo(), only 1 items in docs[] is expected
                                //for debug
                                //if (1 != $scope.treeData.docs.length) {
                                //    console.log("Error!!! only 1 items in docs[] is expected");
                                //}

                                var found = _.find(treeData.docs, {nodeId: $stateParams.id});
                                if (!found) {
                                    var clone = _.clone(treeData.docs);
                                    clone.unshift($scope.treeData.docs[0]);
                                    treeData.docs = clone;
                                }
                                firstLoad = false;
                            }


                        } else {
                            if (0 < treeData.docs.length) {
                                var selectNode = treeData.docs[0];
                                $scope.treeControl.select({
                                    pageStart: start
                                    , nodeType: selectNode.nodeType
                                    , nodeId: selectNode.nodeId
                                });
                            }
                            firstLoad = false;
                        }
                    }

                    $scope.treeData = treeData;
                    return treeData;
                }
            );

            if (firstLoad && $stateParams.id) {
                CallCasesService.getCaseInfo($stateParams.id).then(
                    function (caseInfo) {
                        $scope.treeControl.select({
                            pageStart: start
                            , nodeType: Constant.ObjectTypes.CASE_FILE
                            , nodeId: caseInfo.id
                        });

                        var treeData = {docs: [], total: 0};
                        if ($scope.treeData) {            //It must be set by CallCasesService.queryCasesTreeData()
                            var found = _.find($scope.treeData.docs, {nodeId: $stateParams.id});
                            if (!found) {
                                treeData.docs = _.clone($scope.treeData.docs);
                                treeData.total = $scope.treeData.total;
                                treeData.docs.unshift({
                                    nodeId: Util.goodValue(caseInfo.id, 0)
                                    , nodeType: Constant.ObjectTypes.CASE_FILE
                                    , nodeTitle: Util.goodValue(caseInfo.title)
                                    , nodeToolTip: Util.goodValue(caseInfo.title)
                                });
                            } else {
                                treeData = $scope.treeData; //use what is there already
                            }
                            firstLoad = false;

                        } else {
                            treeData.total = 1;
                            treeData.docs.unshift({
                                nodeId: Util.goodValue(caseInfo.id, 0)
                                , nodeType: Constant.ObjectTypes.CASE_FILE
                                , nodeTitle: Util.goodValue(caseInfo.title)
                                , nodeToolTip: Util.goodValue(caseInfo.title)
                            });
                        }

                        $scope.treeData = treeData;
                        return caseInfo;
                    }
                    , function (errorData) {
                        $scope.treeControl.select({
                            pageStart: start
                            , nodeType: Constant.ObjectTypes.CASE_FILE
                            , nodeId: $stateParams.id
                        });


                        var treeData = {docs: [], total: 0};
                        if ($scope.treeData) {            //It must be set by CallCasesService.queryCasesTreeData()
                            var found = _.find($scope.treeData.docs, {nodeId: $stateParams.id});
                            if (!found) {
                                treeData.docs = _.clone($scope.treeData.docs);
                                treeData.total = $scope.treeData.total;
                                treeData.docs.unshift({
                                    nodeId: $stateParams.id
                                    , nodeType: Constant.ObjectTypes.CASE_FILE
                                    , nodeTitle: $translate.instant("common.directive.objectTree.errorNode.title")
                                    , nodeToolTip: $translate.instant("common.directive.objectTree.errorNode.toolTip")
                                });
                            } else {
                                treeData = $scope.treeData; //use what is there already
                            }
                            firstLoad = false;

                        } else {
                            treeData.total = 1;
                            treeData.docs.unshift({
                                nodeId: $stateParams.id
                                , nodeType: Constant.ObjectTypes.CASE_FILE
                                , nodeTitle: $translate.instant("common.directive.objectTree.errorNode.title")
                                , nodeToolTip: $translate.instant("common.directive.objectTree.errorNode.toolTip")
                            });
                        }

                        $scope.treeData = treeData;
                        return errorData;
                    }
                );
            }

        };

        $scope.onSelect = function (selectedCase) {
            $scope.$emit('req-select-case', selectedCase);
            var components = Util.goodArray(selectedCase.components);
            var componentType = (1 == components.length) ? components[0] : "main";
            $state.go('cases.' + componentType, {
                id: selectedCase.nodeId
            });
        };
    }
]);