angular.module('common').controller('Common.AddPersonModalController', ['$scope', '$modal', '$modalInstance', '$translate'
        , 'Object.LookupService', 'UtilService', 'ConfigService', 'params'
        , function ($scope, $modal, $modalInstance, $translate
            , ObjectLookupService, Util, ConfigService, params) {

            ConfigService.getModuleConfig("common").then(function (moduleConfig) {
                $scope.config = moduleConfig;
                return moduleConfig;
            });

            $scope.selectExisting = 0;
            $scope.types = params.types;
            $scope.showDescription = params.showDescription;

            $scope.radioChanged = function () {
                if ($scope.selectExisting != 0) {
                    $scope.isNew = false;
                    $scope.personId = '';
                    $scope.personName = '';
                    $scope.person = '';
                    $scope.pickPerson();
                }
                else {
                    $scope.isNew = true;
                    $scope.personId = '';
                    $scope.personName = '';
                    $scope.person = '';
                    $scope.addNewPerson();
                }
            };


            $scope.onClickCancel = function () {
                $modalInstance.dismiss('Cancel');
            };

            $scope.onClickOk = function () {
                $modalInstance.close({
                    personId: $scope.personId,
                    description: $scope.description,
                    personType: $scope.type
                });
            };

            $scope.pickPerson = function () {
                var params = {};
                params.header = $translate.instant("common.dialogPersonPicker.header");
                params.filter = '"Object Type": PERSON';
                params.config = Util.goodMapValue($scope.config, "dialogPersonPicker");

                var modalInstance = $modal.open({
                    templateUrl: "modules/common/views/object-picker-modal.client.view.html",
                    controller: ['$scope', '$modalInstance', 'params', function ($scope, $modalInstance, params) {
                        $scope.modalInstance = $modalInstance;
                        $scope.header = params.header;
                        $scope.filter = params.filter;
                        $scope.config = params.config;
                    }],
                    animation: true,
                    size: 'lg',
                    backdrop: 'static',
                    resolve: {
                        params: function () {
                            return params;
                        }
                    }
                });
                modalInstance.result.then(function (selected) {
                    if (!Util.isEmpty(selected)) {
                        $scope.personId = selected.object_id_s;
                        $scope.personName = selected.name;
                    }
                });
            };

            $scope.addNewPerson = function () {

                var modalInstance = $modal.open({
                    scope: $scope,
                    animation: true,
                    templateUrl: 'modules/common/views/new-person-modal.client.view.html',
                    controller: 'Common.NewPersonModalController',
                    size: 'lg'
                });

                modalInstance.result.then(function (data) {
                    $scope.personId = '';
                    $scope.personName = data.person.givenName + ' ' + data.person.familyName;
                    $scope.person = data.person;
                });
            };
        }
    ]
);