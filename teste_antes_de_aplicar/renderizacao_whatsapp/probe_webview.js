// Renders a page in the JavaFX WebView bundled with IPED 4.3.1 and records
// style/interaction facts. It never enables scripts embedded in the page.
// Invocation: jjs -fx --add-modules javafx.web probe_webview.js -- input.html report.json [preview.png]
var File = Java.type('java.io.File');
var Files = Java.type('java.nio.file.Files');
var Paths = Java.type('java.nio.file.Paths');
var StandardCharsets = Java.type('java.nio.charset.StandardCharsets');
var WebView = Java.type('javafx.scene.web.WebView');
var Scene = Java.type('javafx.scene.Scene');
var StackPane = Java.type('javafx.scene.layout.StackPane');
var ChangeListener = Java.type('javafx.beans.value.ChangeListener');
var Platform = Java.type('javafx.application.Platform');
var SnapshotParameters = Java.type('javafx.scene.SnapshotParameters');
var SwingFXUtils = Java.type('javafx.embed.swing.SwingFXUtils');
var ImageIO = Java.type('javax.imageio.ImageIO');

var args = Array.prototype.slice.call(arguments);
var inputPath = args[0];
var reportPath = args[1];
var imagePath = args.length > 2 ? args[2] : '';
var enablePageScripts = args.length > 3 && args[3] === 'enable-js';
var stageRef = null;
var finished = false;
if (!inputPath || !reportPath) throw new Error('input.html and report.json are required');

function finish(code, message) {
    if (finished) return;
    finished = true;
    try {
        if (message) Files.write(Paths.get(reportPath), new java.lang.String(JSON.stringify({error:String(message)})).getBytes(StandardCharsets.UTF_8));
    } catch (writeError) {
        print('REPORT_WRITE_ERROR=' + writeError);
        code = 2;
    }
    print('WEBVIEW_TEST_EXIT=' + code);
    try { stageRef.close(); } catch (ignored) {}
    Platform.exit();
}

function start(stage) {
    stageRef = stage;
    try {
        stage.setTitle('IPED WebView specification test');
        var web = new WebView();
        web.setPrefSize(1100, 760);
        stage.setScene(new Scene(new StackPane(web), 1100, 760));
        stage.show();
        var engine = web.getEngine();
        engine.setJavaScriptEnabled(enablePageScripts);
        var listener = new (Java.extend(ChangeListener))({changed:function(observable, oldValue, newValue) {
            var name = String(newValue);
            if (name === 'SUCCEEDED') {
                try {
                    var probe = String(engine.executeScript('(function(){' +
                        'var nativeRaf=window.requestAnimationFrame;window.requestAnimationFrame=function(callback){callback();return 1;};var nativeAnchorClick=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){nativeAnchorClick.call(this);var event=document.createEvent("Event");event.initEvent("hashchange",false,false);window.dispatchEvent(event);};' +
                        'if(document.body.getAttribute("data-capture-test-fixture")==="true"){var fixtureRows=[],scrollSpacer=document.createElement("div");scrollSpacer.style.height="1500px";document.body.appendChild(scrollSpacer);var fixtureLinks=document.querySelectorAll("a.capture-media-link");' +
                        'Array.prototype.forEach.call(fixtureLinks,function(link){window.scrollTo(0,450);var scrollBefore=window.pageYOffset,fragment=(link.getAttribute("href")||"").substring(1);link.click();var modal=document.getElementById(fragment),visible=!!modal&&getComputedStyle(modal).display!=="none",audio=modal&&modal.querySelector("audio"),video=modal&&modal.querySelector("video"),frame=modal&&modal.querySelector("iframe"),image=modal&&modal.querySelector("img"),mediaStarted=false;' +
                        'try{if(audio){var audioState={paused:true};Object.defineProperty(audio,"paused",{get:function(){return audioState.paused;}});audio.play=function(){audioState.paused=false;return Promise.resolve();};audio.pause=function(){audioState.paused=true;};audio.play();mediaStarted=!audio.paused;}else if(video){var videoState={paused:true};Object.defineProperty(video,"paused",{get:function(){return videoState.paused;}});video.play=function(){videoState.paused=false;return Promise.resolve();};video.pause=function(){videoState.paused=true;};video.play();mediaStarted=!video.paused;}}catch(playError){}' +
                        'var dl=modal&&modal.querySelector("a[download]"),open=modal&&modal.querySelector("a[target=_blank]"),close=modal&&modal.querySelector(".capture-close");if(close)close.click();var scrollAfter=window.pageYOffset;fixtureRows.push({href:link.getAttribute("href"),targetExists:!!modal,visibleAfterOpen:visible,hiddenAfterClose:!!modal&&getComputedStyle(modal).display==="none",scrollRestored:Math.abs(scrollAfter-scrollBefore)<=1,hasImage:!!image,imageLoaded:!!image&&image.complete&&image.naturalWidth>0,hasAudio:!!audio,audioControls:!!audio&&audio.controls,audioPreload:audio?audio.getAttribute("preload"):"",audioAutoplay:!!audio&&audio.autoplay,audioPausedAfterClose:audio?audio.paused:null,hasVideo:!!video,videoControls:!!video&&video.controls,videoPreload:video?video.getAttribute("preload"):"",videoAutoplay:!!video&&video.autoplay,videoPausedAfterClose:video?video.paused:null,mediaStarted:mediaStarted,hasPdfFrame:!!frame,pdfSandbox:frame?frame.getAttribute("sandbox"):null,hasDownload:!!dl,newTabSafe:!!open&&open.rel.indexOf("noopener")>=0&&open.rel.indexOf("noreferrer")>=0});});' +
                        'return JSON.stringify({mode:"attachment-fixture",attachmentTests:fixtureRows,scriptCount:document.querySelectorAll("script").length,csp:document.querySelector("meta[http-equiv=Content-Security-Policy]")?document.querySelector("meta[http-equiv=Content-Security-Policy]").content:""});}' +
                        'function info(e){if(!e)return null;var s=getComputedStyle(e);return {tag:e.tagName.toLowerCase(),id:e.id||"",className:typeof e.className==="string"?e.className:"",display:s.display,color:s.color,backgroundColor:s.backgroundColor,borderRadius:s.borderRadius,padding:s.padding,fontFamily:s.fontFamily,fontSize:s.fontSize};}' +
                        'var ids=["243188","243189","243190"],rows=[];' +
                        'ids.forEach(function(id){var e=document.getElementById(id);if(!e){rows.push({id:id,found:false});return;}' +
                        'var nodes=[e].concat([].slice.call(e.querySelectorAll(".bbr,.incoming,.outgoing,.time,.sender,.from,.thumb,img,audio,video"))).map(info);' +
                        'rows.push({id:id,found:true,className:e.className,nodes:nodes,hasText:!!(e.innerText||e.textContent||"").trim()});});' +
                        'var b=getComputedStyle(document.body),style=document.querySelector("style"),top=document.querySelector("#capture-topbar")||document.querySelector("#topbar"),conv=document.querySelector("#capture-conversation")||document.querySelector("#conversation");' +
                        'var css=style?(style.textContent||""):"",urls=css.match(/url\\s*\\([^)]*\\)/gi)||[],urlKinds={dataImage:0,dataOther:0,remote:0,file:0,relative:0},dataMimeCounts={},badUrl=false;' +
                        'urls.forEach(function(u){var v=u.substring(u.indexOf("(")+1,u.lastIndexOf(")")).replace(/^\\s*[\"\\\']|[\"\\\']\\s*$/g,"").trim().toLowerCase();' +
                        'if(/^data:image\\/(?:png|jpe?g|gif|webp);base64,/.test(v))urlKinds.dataImage++;else if(/^data:/.test(v)){urlKinds.dataOther++;var mime=(v.match(/^data:([^;,]+)/)||[])[1]||"unknown";dataMimeCounts[mime]=(dataMimeCounts[mime]||0)+1;}else if(/^https?:|^\\/\\//.test(v)){urlKinds.remote++;badUrl=true;}else if(/^file:/.test(v)){urlKinds.file++;badUrl=true;}else{urlKinds.relative++;badUrl=true;}});' +
                        'var children=conv?[].slice.call(conv.children).map(info):[],svgRules=[];' +
                        'function inspectSvg(rule){var value=rule.style?rule.style.getPropertyValue("background-image"):"",m=value.match(/url\\([\\"\\\']?(data:image\\/svg\\+xml[^\\"\\\']+)[\\"\\\']?\\)/i);if(!m)return {safe:false,reason:"non-data-svg"};' +
                        'var uri=m[1],comma=uri.indexOf(","),payload=comma<0?"":uri.substring(comma+1),meta=comma<0?"":uri.substring(5,comma),raw="";try{raw=/;base64/i.test(meta)?atob(payload):decodeURIComponent(payload);}catch(e){return {safe:false,reason:"decode"};}' +
                        'var x=new DOMParser().parseFromString(raw,"image/svg+xml"),root=x.documentElement,all=x.getElementsByTagName("*");if(!root||root.localName!=="svg"||x.getElementsByTagName("parsererror").length)return {safe:false,reason:"xml"};' +
                        'for(var ai=0;ai<all.length;ai++){var attrs=all[ai].attributes||[];if(String(all[ai].localName).toLowerCase()==="script"||String(all[ai].localName).toLowerCase()==="foreignobject")return {safe:false,reason:"active-element"};for(var aj=0;aj<attrs.length;aj++){var an=attrs[aj].name.toLowerCase(),av=attrs[aj].value||"";if(an.indexOf("on")===0||((an==="href"||an==="xlink:href")&&av.charAt(0)!=="#")||/url\\s*\\(\\s*(?:https?:|file:|javascript:|\\/\\/)/i.test(av))return {safe:false,reason:"active-attribute"};}}' +
                        'return {safe:true,elementCount:all.length};}' +
                        'if(document.styleSheets.length){try{var cr=document.styleSheets[0].cssRules;for(var ci=0;ci<cr.length;ci++){var rule=cr[ci],text=rule.cssText||"";if(/data:image\\/svg\\+xml/i.test(text)){var check=inspectSvg(rule);check.selector=rule.selectorText||rule.type;check.properties=rule.style?Array.prototype.map.call(rule.style,function(k){return k;}):[];svgRules.push(check);}}}catch(ignoreRules){}}' +
                        'var captureKeys=[].slice.call(document.querySelectorAll("[data-capture-message-key]")),openLink=null,viewerTest=null;' +
                        'Array.prototype.some.call(document.querySelectorAll("a.capture-media-link"),function(a){if((a.getAttribute("href")||"").indexOf("#preview-")===0){openLink=a;return true;}return false;});' +
                        'if(openLink){var scrollSpacer=document.createElement("div");scrollSpacer.style.height="1500px";document.body.appendChild(scrollSpacer);window.scrollTo(0,450);var scrollBefore=window.pageYOffset,targetId=(openLink.getAttribute("href")||"").substring(1);openLink.click();var modal=document.getElementById(targetId),visible=!!modal&&getComputedStyle(modal).display!=="none",preview=modal&&modal.querySelector("img"),dl=modal&&modal.querySelector("a[download]"),open=modal&&modal.querySelector("a[target=_blank]"),close=modal&&modal.querySelector(".capture-close");if(close)close.click();viewerTest={targetExists:!!modal,visibleAfterOpen:visible,hiddenAfterClose:!!modal&&getComputedStyle(modal).display==="none",scrollRestored:Math.abs(window.pageYOffset-scrollBefore)<=1,imageLoaded:!!preview&&preview.complete&&preview.naturalWidth>0,hasDownload:!!dl,newTabSafe:!!open&&open.rel.indexOf("noopener")>=0&&open.rel.indexOf("noreferrer")>=0};}' +
                        'return JSON.stringify({mode:captureKeys.length?"capture":"source",title:document.title,body:{backgroundColor:b.backgroundColor,backgroundImage:b.backgroundImage?b.backgroundImage.substring(0,40):"",fontFamily:b.fontFamily},' +
                        'captureMessageCount:captureKeys.length,originalMessageIdsRemoved:!document.getElementById("243188")&&!document.getElementById("243189")&&!document.getElementById("243190"),viewerTest:viewerTest,' +
                        'topbar:info(top),conversation:info(conv),bodyChildren:[].slice.call(document.body.children).map(info),conversationChildren:children,svgRules:svgRules,styleBytes:css.length,styleSheetCount:document.styleSheets.length,cssUrlCount:urls.length,cssUrlKinds:urlKinds,cssDataMimeCounts:dataMimeCounts,cssHasExternalUrl:badUrl,cssHasImport:/@import/i.test(css),idsCount:document.querySelectorAll("div.linha[id]").length,rows:rows});' +
                        '})()'));
                    Files.write(Paths.get(reportPath), new java.lang.String(probe).getBytes(StandardCharsets.UTF_8));
                    if (imagePath) {
                        var snapshot = web.snapshot(new SnapshotParameters(), null);
                        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), 'png', new File(imagePath));
                    }
                    finish(0, '');
                } catch (error) {
                    finish(3, error);
                }
            } else if (name === 'FAILED' || name === 'CANCELLED') {
                finish(4, 'WebEngine load state: ' + name);
            }
        }});
        engine.getLoadWorker().stateProperty().addListener(listener);
        engine.load(new File(inputPath).toURI().toURL().toExternalForm());
    } catch (error) {
        finish(5, error);
    }
}
