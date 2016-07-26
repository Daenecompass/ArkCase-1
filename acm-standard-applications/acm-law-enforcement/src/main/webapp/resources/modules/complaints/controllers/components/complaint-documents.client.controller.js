'use strict';

angular.module('complaints').controller('Complaints.DocumentsController', ['$scope', '$stateParams', '$modal', '$q', '$timeout'
    , 'UtilService', 'ConfigService', 'ObjectService', 'Object.LookupService', 'Complaint.InfoService'
    , 'Helper.ObjectBrowserService', 'DocTreeService', 'Authentication', 'PermissionsService', 'Object.ModelService'
    , 'DocTreeExt.Core'
    , function ($scope, $stateParams, $modal, $q, $timeout
        , Util, ConfigService, ObjectService, ObjectLookupService, ComplaintInfoService
        , HelperObjectBrowserService, DocTreeService, Authentication, PermissionsService, ObjectModelService
        , DocTreeExtCore) {


        Authentication.queryUserInfo().then(
            function (userInfo) {
                $scope.user = userInfo.userId;
                return userInfo;
            }
        );

        ObjectLookupService.getFormTypes(ObjectService.ObjectTypes.COMPLAINT).then(
            function (formTypes) {
                $timeout(function() {
                    $scope.fileTypes = $scope.fileTypes || [];
                    $scope.fileTypes = $scope.fileTypes.concat(Util.goodArray(formTypes));
                }, 0);
                return formTypes;
            }
        );
        ObjectLookupService.getFileTypes().then(
            function (fileTypes) {
                $timeout(function() {
                    $scope.fileTypes = $scope.fileTypes || [];
                    $scope.fileTypes = $scope.fileTypes.concat(Util.goodArray(fileTypes));
                }, 0);
                return fileTypes;
            }
        );

        ObjectLookupService.getComplaintCorrespondenceForms().then(
            function (correspondenceForms) {
                $timeout(function() {
                    $scope.correspondenceForms = Util.goodArray(correspondenceForms);
                }, 0);
                return correspondenceForms;
            }
        );

        $scope.uploadForm = function (type, folderId, onCloseForm) {
            return DocTreeService.uploadFrevvoForm(type, folderId, onCloseForm, $scope.objectInfo, $scope.fileTypes);
        };

        var componentHelper = new HelperObjectBrowserService.Component({
            scope: $scope
            , stateParams: $stateParams
            , moduleId: "complaints"
            , componentId: "documents"
            , retrieveObjectInfo: ComplaintInfoService.getComplaintInfo
            , validateObjectInfo: ComplaintInfoService.validateComplaintInfo
            , onConfigRetrieved: function (componentConfig) {
                return onConfigRetrieved(componentConfig);
            }
            , onObjectInfoRetrieved: function (objectInfo) {
                onObjectInfoRetrieved(objectInfo);
            }
        });

        var onConfigRetrieved = function (config) {
            $scope.config = config;
            $scope.treeConfig = config.docTree;
            $scope.allowParentOwnerToCancel = config.docTree.allowParentOwnerToCancel;
        };


        $scope.objectType = ObjectService.ObjectTypes.COMPLAINT;
        $scope.objectId = componentHelper.currentObjectId; //$stateParams.id;
        var onObjectInfoRetrieved = function (objectInfo) {
            $scope.objectInfo = objectInfo;
            $scope.objectId = objectInfo.complaintId;
            $scope.assignee = ObjectModelService.getAssignee(objectInfo);
        };

        $scope.onInitTree = function(treeControl) {
            $scope.treeControl = treeControl;
            DocTreeExtCore.handleCheckout(treeControl, $scope);
            DocTreeExtCore.handleCheckin(treeControl, $scope);
            DocTreeExtCore.handleEditWithWord(treeControl, $scope);
            DocTreeExtCore.handleCancelEditing(treeControl, $scope);
        };


        $scope.onClickRefresh = function () {
            $scope.treeControl.refreshTree();
        };

    }
]);