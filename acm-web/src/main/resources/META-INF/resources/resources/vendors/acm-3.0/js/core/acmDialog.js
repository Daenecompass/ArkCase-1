/**
 * Acm.Dialog is used to display Dialog/confirm/error messages to the user
 *
 * Current implementation is to use bootbox
 *
 * @author jwu
 */
Acm.Dialog = {
    create : function() {
    }

    ,DEFAULT_NO_TITLE:    undefined
    ,DEFAULT_NO_CALLBACK: undefined

    //
    // callback example:
    // function() {
    //    alert("callback");
    // }
    //
    ,info: function(msg, callback, title){
        var opt = {
            message: msg
        }
        if(Acm.isNotEmpty(title)) {
            opt.title = title;
        } else {
            opt.title = "Info";
        }
        if (Acm.isNotEmpty(callback)) {
            opt.callback = callback;
        }

        bootbox.alert(opt);
    }
    ,alert: function(msg, callback, title){
        if(Acm.isEmpty(title)) {
            title = "Alert";
        }
        this.info(msg, callback, title);
    }
    ,error: function(msg, callback, title){
        if(Acm.isEmpty(title)) {
            title = "Error";
        }
        this.info(msg, callback, title);
    }

    //
    // Usage example:
    // Acm.Dialog.confirm("Are you sure?"
    //     ,function(result) {
    //         if (result == true) {
    //             alert("Do it");
    //         } else {
    //             alert("Do nothing");
    //         }
    //     }
    //     ,"My Title"
    // }
    //
    ,confirm: function(msg, callback, title){
        if (Acm.isEmpty(callback)) {
            console.log("Confirm dialog needs callback");
            return;
        }

        bootbox.confirm(msg, callback);
    }

    //
    // callback example:
    // function(result) {
    //    if (null === result) {
    //        alert("Prompt dismissed");
    //    } else {
    //        alert("Prompt result:" + result);
    //    }
    // }
    //
    ,prompt: function(msg, callback, title){
        if (Acm.isEmpty(callback)) {
            console.log("Prompt dialog needs callback");
            return;
        }

        bootbox.prompt(msg, callback);
    }

    ,bootstrapModal: function($s, onClickBtnPrimary, onClickBtnDefault) {
        if (onClickBtnPrimary) {
            $s.find("button.btn-primary").unbind("click").on("click", function(e){
                onClickBtnPrimary(e, this);
                $s.modal("hide");
            });
        }
        if (onClickBtnDefault) {
            $s.find("button.btn-default").unbind("click").on("click", function(e){onClickBtnDefault(e, this);});
        }

        $s.modal("show");
    }


    ,openWindow: function(url, title, w, h, onDone) {

        var dualScreenLeft = window.screenLeft != undefined ? window.screenLeft : screen.left;
        var dualScreenTop = window.screenTop != undefined ? window.screenTop : screen.top;

        width = window.innerWidth ? window.innerWidth : document.documentElement.clientWidth ? document.documentElement.clientWidth : screen.width;
        height = window.innerHeight ? window.innerHeight : document.documentElement.clientHeight ? document.documentElement.clientHeight : screen.height;

        var left = ((width / 2) - (w / 2)) + dualScreenLeft;
        var top = ((height / 2) - (h / 2)) + dualScreenTop;
        var newWindow = window.open(url, title, 'scrollbars=yes, resizable=1, width=' + w + ', height=' + h + ', top=' + top + ', left=' + left);


        if (window.focus) {
            newWindow.focus();
        }

        this._checkClosePopup(newWindow, onDone);
    }

    ,_checkClosePopup: function(newWindow, onDone){
        var timer = setInterval(function() {
            if(newWindow.closed) {
                clearInterval(timer);
                if (onDone) {
                    onDone();
                }
            }
        }, 1000);
    }
}