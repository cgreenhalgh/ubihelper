// Ubihelper client
//
// Chris Greenhalgh, University of Nottingham, 2013

// API: create one instance - will start polling immediately
function Ubihelper(pollInterval, onError, url) {
    if (pollInterval)
        this.pollInterval = pollInterval;
    else
        this.pollInterval = 250;
    if (url)
        this.url = url;
    else
        this.url = 'http://127.0.0.1:8180/ubihelper';
    this.init();
    this.onError = onError;
    // name -> { period: P, listener: L(value) }
    this.channels = {};
    this.init();
}

// API: register interest in and listener for a channel
Ubihelper.prototype.watch = function(name,listener,period) {
    var info = { name: name, listener: listener, period: (period? period/1000 : this.pollInterval/1000) };
    this.channels[name] = info;
    log('watch '+name+', period='+info.period);
};

// internal initialise
Ubihelper.prototype.init = function() {
    // simulator?! - check phonegap device
    //var device = window.device;
    if (device===undefined || device.platform===undefined || (device.platform!='Android' && device.platform!='iPhone')) {
        window.ubihelper = this;
        // create window (if not closed won't reset, though)
        var my_window;
        my_window = window.open("", "ubihelper", "status=1,width=350,height=150");
        my_window.close();
        
        my_window = window.open("", "ubihelper", "status=no,width=350,height=250,location=no,titlebar=no");
        my_window.document.write('<h1>Ubihelper sensor input</h1>');
        my_window.document.write('<script src="http://the.appfurnace.com/toolkit/shared/libs/jquery/jquery-1.6.1.js"></script>');
        my_window.document.write('<script src="https://raw.github.com/douglascrockford/JSON-js/master/json2.js"></script>');
        my_window.document.write('<p>Channel:</p>');
        my_window.document.write('<input type="text" id="name" value="accelerometer"/>');
        my_window.document.write('<p>Value:</p>');
        my_window.document.write('<input type="text" id="value""></input>');
        my_window.document.write('<script>function sendValue(name,value) { window.opener.ubihelper.handleValue(name,value); }</script>');
        my_window.document.write('<input type="button" value="Set" onClick="sendValue($(\'#name\').val(),JSON.parse($(\'#value\').val()))"/>');
        my_window.focus();
        
        // other code I might use at some point...
        //my_window.document.write('<div id="accelerometer_area" style="width:100px;height:100px;background-color:#ccc"></div>');
        //my_window.document.write('<script>$("#accelerometer_area").mousedown(function(event) { var x=event.pageX-this.offsetLeft; var y=event.pageY-this.offsetTop; var v={}; v.values=[20*(x-50)/50,20*(y-50)/50,0]; $(\'#accelerometer\').val(JSON.stringify(v));  sendValue("accelerometer",v); });</script>');
    } else {
        log('platform'+'='+window.device.platform);
        var self = this;
        setTimeout(function() { self.poll(); }, this.pollInterval);
    }    
};

// internal handle received value from real poll or simulator
Ubihelper.prototype.handleValue = function(name,value) {
    //log('handleValue('+name+','+JSON.stringify(value)+')');
    var info = this.channels[name];
    if (info && info.listener) {
        try {
            info.listener(name, value);
        }
        catch (err) {
            log('listener '+name+' error: '+err.message);
        }            
    }
};

// internal - periodic poll function
Ubihelper.prototype.poll = function() {
    //  default URL
    var query = [];
    for (var name in this.channels) {
        var info =this.channels[name];
        query.push({name:name,period:info.period});
    }
    var self = this;
    try {
        $.ajax(
        {
            url:this.url,
            type:"POST",
            data:JSON.stringify(query),
            // Note: seems to cause an error, at least in 1.2 player: dataType:'json',
            complete:function() {
                setTimeout(function() { self.poll(); }, this.pollInterval);
            },
            error:function(xhr,status,error) {
                log('Ubihelper http error: '+status+' - '+error);
                if (self.onError) {
                    try {
                        self.onError(status, error);
                    }
                    catch (err) {
                        log('onError error: '+err.message);
                    }
                }
            },
            success:function(data,status,xhr) {
                for (var i in data) {
                    var el = data[i];
                    for (var vi in el.values) {
                        var val = el.values[vi];
                        self.handleValue(el.name, val);
                    }
                }
            }
        });
    } catch (err) {
        log('Exception: '+err.message);
    }
};

//=========================================================================================
