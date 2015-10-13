'use strict';

angular.module('directives').directive('searchModal', ['SearchService',
    function (SearchService) {
        return {
            restrict: 'E',              //match only element name
            scope: {
                //supplied by the caller
                header: '@',            //@ : text binding (read-only and only strings)
                search: '@',
                cancel: '@',
                ok: '@',
                searchPlaceholder: '@',
                filter: '@',
                config: '&',            //& : one way binding (read-only, can return key, value pair via a getter function)
                modalInstance: '='      //= : two way binding (read-write both, parent scope and directive's isolated scope have two way binding)
            },

            link: function (scope) {    //dom operations
                scope.facets=[];
                scope.currentFacetSelection = [];
                scope.selectedItem = null;
                scope.queryExistingItems = function (){
                    SearchService.queryFilteredSearch({
                            input: scope.searchQuery + "*",
                            start: scope.start,
                            n: scope.pageSize,
                            filters: scope.filter
                        },
                        function (data) {
                            updateFacets(data.facet_counts.facet_fields);
                            scope.gridOptions.data = data.response.docs;
                            scope.gridOptions.totalItems = data.response.numFound;
                        });
                };

                function updateFacets(facets){
                    if(facets){
                        if(scope.facets.length){
                            scope.facets.splice(0,scope.facets.length)
                        }
                        _.forEach(facets, function(value, key) {
                            if(value){
                                scope.facets.push({"name": key, "fields":value});
                            }
                        });
                    }
                }

                scope.selectFacet = function (checked, facet, field){
                    if(checked){
                        scope.filter += '%26fq="' + facet + '":' + field;
                        scope.queryExistingItems();
                    }else{
                        if(scope.filter.indexOf('%26fq="' + facet + '":' + field) > -1){
                            scope.filter = scope.filter.split('%26fq="' + facet + '":' + field).join('');
                            scope.queryExistingItems();
                        }
                    }
                }

                scope.addExistingItem = function() {
                    //when the modal is closed, the parent scope gets
                    //the selectedItem via the two-way binding
                    scope.modalInstance.close(scope.selectedItem);
                }

                scope.close = function() {
                    scope.modalInstance.dismiss('cancel')
                }

                //prepare the UI-grid
                if (scope.config()) {
                    scope.pageSize = scope.config().paginationPageSize;
                    scope.start = scope.config().start;
                    scope.gridOptions = {
                        enableColumnResizing: true,
                        enableRowSelection: true,
                        enableRowHeaderSelection: false,
                        enableFiltering: scope.config().enableFiltering,
                        multiSelect: false,
                        noUnselect: false,
                        useExternalPagination: true,
                        paginationPageSizes: scope.config().paginationPageSizes,
                        paginationPageSize: scope.config().paginationPageSize,
                        columnDefs: scope.config().columnDefs,
                        onRegisterApi: function (gridApi) {
                            scope.gridApi = gridApi;

                            gridApi.selection.on.rowSelectionChanged(scope, function (row) {
                                scope.selectedItem = row.isSelected ? row.entity : null;
                            });


                            gridApi.pagination.on.paginationChanged(scope, function (newPage, pageSize) {
                                scope.start = (newPage - 1) * pageSize;   //newPage is 1-based index
                                scope.pageSize = pageSize;
                                scope.queryExistingItems();
                            });
                        }
                    }
                }
            },

            templateUrl: 'directives/search-modal/search-modal.client.view.html'
        };
    }
]);