/**
 * ComplaintList.Service
 *
 * manages all service call to application server
 *
 * @author jwu
 */
ComplaintList.Service = {
    initialize : function() {
    }

    ,API_LIST_COMPLAINT         : "/api/latest/plugin/complaint/list"
    ,API_RETRIEVE_DETAIL        : "/api/latest/plugin/complaint/byId/"
    ,API_DOWNLOAD_DOCUMENT      : "/api/v1/plugin/ecm/download/byId/"
    ,API_UPLOAD_COMPLAINT_FILE  : "/api/latest/plugin/complaint/file"
    ,API_RETRIEVE_TASKS         : "/api/v1/plugin/search/quickSearch?q="


    ,listComplaint : function() {
        Acm.Ajax.asyncGet(Acm.getContextPath() + this.API_LIST_COMPLAINT
            ,ComplaintList.Callback.EVENT_LIST_RETRIEVED
        );
    }

    ,retrieveDetail : function(complaintId) {
        Acm.Ajax.asyncGet(Acm.getContextPath() + this.API_RETRIEVE_DETAIL + complaintId
            ,ComplaintList.Callback.EVENT_DETAIL_RETRIEVED
        );
    }

    ,retrieveTasks : function(complaintId) {
        //Acm.Ajax.asyncGet(Acm.getContextPath() + this.API_RETRIEVE_TASKS + complaintId
        Acm.Ajax.asyncGet(Acm.getContextPath() + "/api/v1/plugin/search/quickSearch?q=object_type_s:Task&start=0&n=800&s="
            ,ComplaintList.Callback.EVENT_TASKS_RETRIEVED
        );
    }


};

