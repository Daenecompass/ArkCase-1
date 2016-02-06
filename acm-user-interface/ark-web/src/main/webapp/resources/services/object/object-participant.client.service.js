'use strict';

/**
 * @ngdoc service
 * @name services:Object.ParticipantService
 *
 * @description
 *
 * {@link https://github.com/Armedia/ACM3/blob/develop/acm-user-interface/ark-web/src/main/webapp/resources/services/object/object-participant.client.service.js services/object/object-participant.client.service.js}

 * Object.ParticipantService includes group of REST calls related to participants.
 */
angular.module('services').factory('Object.ParticipantService', ['$resource', '$translate', 'UtilService',
    function ($resource, $translate, Util) {
        var Service = $resource('proxy/arkcase/api/v1/service', {}, {

            /**
             * @ngdoc method
             * @name get
             * @methodOf services:Object.ParticipantService
             *
             * @description
             * Query list of participants for an object.
             *
             * @param {Object} params Map of input parameter
             * @param {String} params.objectType  Object type
             * @param {String} params.objectId  Object ID
             * @param {Function} onSuccess (Optional)Callback function of success query
             * @param {Function} onError (Optional) Callback function when fail
             *
             * @returns {Object} Object returned by $resource
             */
            get: {
                method: 'GET',
                url: 'proxy/arkcase/api/v1/service/participant/:objectType/:objectId',
                isArray: true
            },

            /**
             * @ngdoc method
             * @name save
             * @methodOf services:Object.ParticipantService
             *
             * @description
             * Create a new participant.
             *
             * @param {Object} params Map of input parameter
             * @param {String} params.userId  User ID
             * @param {String} params.participantType  Participant Type
             * @param {String} params.objectType  Object type
             * @param {String} params.objectId  Object ID
             * @param {Function} onSuccess (Optional)Callback function of success query
             * @param {Function} onError (Optional) Callback function when fail
             *
             * @returns {Object} Object returned by $resource
             */
            save: {
                method: 'PUT',
                url: 'proxy/arkcase/api/v1/service/participant/:userId/:participantType/:objectType/:objectId',
                cache: false
            },

            /**
             * @ngdoc method
             * @name delete
             * @methodOf services:Object.ParticipantService
             *
             * @description
             * Delete participant.
             *
             * @param {Object} params Map of input parameter
             * @param {String} params.userId  User ID
             * @param {String} params.participantType  Participant Type
             * @param {String} params.objectType  Object type
             * @param {String} params.objectId  Object ID
             * @param {Function} onSuccess (Optional)Callback function of success query
             * @param {Function} onError (Optional) Callback function when fail
             *
             * @returns {Object} Object returned by $resource
             */
            delete: {
                method: 'DELETE',
                url: 'proxy/arkcase/api/v1/service/participant/:userId/:participantType/:objectType/:objectId',
                cache: false
            },

            /**
             * @ngdoc method
             * @name changeRole
             * @methodOf services:Object.ParticipantService
             *
             * @description
             * Change role for participant.
             *
             * @param {Object} params Map of input parameter
             * @param {String} params.participantId  Participant ID
             * @param {String} params.participantType  Participant Type
             * @param {Function} onSuccess (Optional)Callback function of success query
             * @param {Function} onError (Optional) Callback function when fail
             *
             * @returns {Object} Object returned by $resource
             */
            changeRole: {
                method: 'DELETE',
                url: 'proxy/arkcase/api/v1/service/participant/:participantId/:participantType',
                cache: false
            }

        });

        /**
         * @ngdoc method
         * @name retrieveParticipants
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Query list of participants of an object
         *
         * @param {String} objectType  Object type
         * @param {Number} objectId  Object ID
         *
         * @returns {Object} Promise
         */
        Service.retrieveParticipants = function (objectType, objectId) {
            return Util.serviceCall({
                service: Service.get
                , param: {
                    objectType: objectType,
                    objectId: objectId
                }
                , onSuccess: function (data) {
                    if (Service.validateParticipants(data)) {
                        return data;
                    }
                }
            })
        };

        /**
         * @ngdoc method
         * @name addNewParticipant
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Query list of participants of an object
         *
         * @param {String} userId  User ID
         * @param {String} participantType  Participant Type
         * @param {String} objectType  Object type
         * @param {Number} objectId  Object ID
         *
         * @returns {Object} Promise
         */
        Service.addNewParticipant = function (userId, participantType, objectType, objectId) {
            return Util.serviceCall({
                service: Service.save
                , param: {
                    userId: userId,
                    participantType: participantType,
                    objectType: objectType,
                    objectId: objectId
                }
                , data: {}
                , onSuccess: function (data) {
                    if (Service.validateParticipant(data)) {
                        return data;
                    }
                }
            })
        };

        /**
         * @ngdoc method
         * @name removeParticipant
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Remove participant of an object.
         *
         * @param {String} userId  User ID
         * @param {String} participantType  Participant Type
         * @param {String} objectType  Object type
         * @param {Number} objectId  Object ID
         *
         * @returns {Object} Promise
         */
        Service.removeParticipant = function(userId, participantType, objectType, objectId) {
            return Util.serviceCall({
                service: Service.delete
                , param: {
                    userId: userId,
                    participantType: participantType,
                    objectType: objectType,
                    objectId: objectId
                }
                , onSuccess: function (data) {
                    if (Service.validateRemovedParticipant(data)) {
                        return data;
                    }
                }
            })
        };

        /**
         * @ngdoc method
         * @name changeParticipantRole
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Change role for participant of an object.
         *
         * @param {String} participantId  Participant ID
         * @param {String} participantType  Participant Type
         *
         * @returns {Object} Promise
         */
        Service.changeParticipantRole = function(participantId, participantType) {
            return Util.serviceCall({
                service: Service.changeRole
                , param: {
                    participantId: participantId,
                    participantType: participantType
                }
                , onSuccess: function (data) {
                    if (Service.validateParticipant(data)) {
                        return data;
                    }
                }
            })
        };

        /**
         * @ngdoc method
         * @name validateParticipants
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Validate participants.
         *
         * @param {Object} data  Data to be validated
         *
         * @returns {Object} Promise
         */
        Service.validateParticipants = function (data) {
            if (Util.isEmpty(data)) {
                return false;
            }
            if (!Util.isArray(data)) {
                return false;
            }
            return true;
        };

        /**
         * @ngdoc method
         * @name validateParticipant
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Validate participant.
         *
         * @param {Object} data  Data to be validated
         *
         * @returns {Object} Promise
         */
        Service.validateParticipant = function (data) {
            if (Util.isEmpty(data)) {
                return false;
            }
            if (Util.isEmpty(data.id)) {
                return false;
            }
            if (Util.isEmpty(data.objectType)) {
                return false;
            }
            if (Util.isEmpty(data.objectId)) {
                return false;
            }
            if (Util.isEmpty(data.participantType)) {
                return false;
            }
            if (Util.isEmpty(data.participantLdapId)) {
                return false;
            }
            return true;
        };

        /**
         * @ngdoc method
         * @name validateRemovedParticipant
         * @methodOf services:Object.ParticipantService
         *
         * @description
         * Validate participant that will be removed.
         *
         * @param {Object} data  Data to be validated
         *
         * @returns {Object} Promise
         */
        Service.validateRemovedParticipant = function(data){
            if (Util.isEmpty(data)) {
                return false;
            }
            if (Util.isEmpty(data.deletedParticipant)) {
                return false;
            }
            if (Util.isEmpty(data.deletedParticipantId)) {
                return false;
            }
            return true;
        };

        return Service;
    }
]);