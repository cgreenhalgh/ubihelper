// Here's where you should write your functions

// common callback on channel value recevied from ubihelper
function onChannelValue(name, value) {
    // check name and handle here...
    if (name=='time')
        ui.output.text('time is now '+value.time);
    else {
        ui.output.text('oh: '+name+' = '+JSON.stringify(value));
        // ...?
    }
}

//=========================================================================================
// Ubihelper configuration
// e.g. 4/second
var pollInterval = 0.25;
// e.g. bluetooth
var ubihelperQuery = '[{"name":"time","period":0.25}]';
// default
var ubihelperUrl = "http://127.0.0.1:8180/ubihelper";

//=========================================================================================
// Generic Ubihelper support  - shouldn't need changing

// simulator?! - check phonegap device
var device = window.device;
if (device===undefined || device.platform===undefined) {
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
    my_window.document.write('<script>function sendValue(name,value) { window.opener.onChannelValue(name,value); }</script>');
    my_window.document.write('<input type="button" value="Set" onClick="sendValue($(\'#name\').val(),JSON.parse($(\'#value\').val()))"/>');
    my_window.focus();
    
    // other code I might use at some point...
    //my_window.document.write('<div id="accelerometer_area" style="width:100px;height:100px;background-color:#ccc"></div>');
    //my_window.document.write('<script>$("#accelerometer_area").mousedown(function(event) { var x=event.pageX-this.offsetLeft; var y=event.pageY-this.offsetTop; var v={}; v.values=[20*(x-50)/50,20*(y-50)/50,0]; $(\'#accelerometer\').val(JSON.stringify(v));  sendValue("accelerometer",v); });</script>');
} else {
    ui.output.text('platform'+'='+window.device.platform);
    setTimeout("pollUbihelper()", 1000*pollInterval);
}

var count = 0;
function pollUbihelper() {
    count = count+1;
//    ui.output.text(ui.output.text()+".");
    //  default URL
    try {
        $.ajax(
        {
            url:ubihelperUrl,
            type:"POST",
            data:ubihelperQuery,
            dataType:'json',
            complete:function() {
                setTimeout("pollUbihelper()", 1000*pollInterval);
            },
            error:function(xhr,status,error) {
                log('Error: '+status+' - '+error);            
                //ui.output.text('Error: '+status+' - '+error);    
            },
            success:function(data,status,xhr) {
                for (var i in data) {
                    var el = data[i];
                    for (var vi in el.values) {
                        var val = el.values[vi];
                        onChannelValue(el.name, val);
                    }
                }
//              ui.output.text('Got '+JSON.stringify(data));    
            }
        });
    } catch (err) {
//        ui.output.text('Exception: '+err.message);
    }
}

//=========================================================================================
// Here's where you should write your functions

// common callback on channel value recevied from ubihelper
function onChannelValue(name, value) {
    // check name and handle here...
    if (name=='time')
        ui.output.text('time is now '+value.time);
    else {
        ui.output.text('oh: '+name+' = '+JSON.stringify(value));
        // ...?
    }
}

//=========================================================================================
// Ubihelper configuration
// e.g. 4/second
var pollInterval = 0.25;
// e.g. bluetooth
var ubihelperQuery = '[{"name":"time","period":0.25}]';
// default
var ubihelperUrl = "http://127.0.0.1:8180/ubihelper";

//=========================================================================================
// Generic Ubihelper support  - shouldn't need changing

// simulator?! - check phonegap device
var device = window.device;
if (device===undefined || device.platform===undefined) {
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
    my_window.document.write('<script>function sendValue(name,value) { window.opener.onChannelValue(name,value); }</script>');
    my_window.document.write('<input type="button" value="Set" onClick="sendValue($(\'#name\').val(),JSON.parse($(\'#value\').val()))"/>');
    my_window.focus();
    
    // other code I might use at some point...
    //my_window.document.write('<div id="accelerometer_area" style="width:100px;height:100px;background-color:#ccc"></div>');
    //my_window.document.write('<script>$("#accelerometer_area").mousedown(function(event) { var x=event.pageX-this.offsetLeft; var y=event.pageY-this.offsetTop; var v={}; v.values=[20*(x-50)/50,20*(y-50)/50,0]; $(\'#accelerometer\').val(JSON.stringify(v));  sendValue("accelerometer",v); });</script>');
} else {
    ui.output.text('platform'+'='+window.device.platform);
    setTimeout("pollUbihelper()", 1000*pollInterval);
}

var count = 0;
function pollUbihelper() {
    count = count+1;
//    ui.output.text(ui.output.text()+".");
    //  default URL
    try {
        $.ajax(
        {
            url:ubihelperUrl,
            type:"POST",
            data:ubihelperQuery,
            dataType:'json',
            complete:function() {
                setTimeout("pollUbihelper()", 1000*pollInterval);
            },
            error:function(xhr,status,error) {
                log('Error: '+status+' - '+error);            
                //ui.output.text('Error: '+status+' - '+error);    
            },
            success:function(data,status,xhr) {
                for (var i in data) {
                    var el = data[i];
                    for (var vi in el.values) {
                        var val = el.values[vi];
                        onChannelValue(el.name, val);
                    }
                }
//              ui.output.text('Got '+JSON.stringify(data));    
            }
        });
    } catch (err) {
//        ui.output.text('Exception: '+err.message);
    }
}

//=========================================================================================
