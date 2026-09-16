package org.kazumi.tv.playback

/** ES5 syntax for old Android WebViews; no privileged JavaScript bridge. */
object MediaDiscoveryScript {
    val poll = """
        (function(){
          function setup(w,depth){try{
            if(!w.__kazumiMedia){
              var q=[]; w.__kazumiMedia=q;
              function add(u){try{if(!u||typeof u!=='string')return;var a=w.document.createElement('a');a.href=u;
                if(/^https?:/i.test(a.href)&&q.indexOf(a.href)<0&&q.length<24)q.push(a.href);}catch(e){}}
              w.__kazumiAdd=add;
              if(w.Response&&w.Response.prototype.text){var text=w.Response.prototype.text;w.Response.prototype.text=function(){
                var response=this;return text.apply(this,arguments).then(function(body){if(/^\s*#EXTM3U/.test(body))add(response.url);return body;});};}
              function scan(){var n=w.document.querySelectorAll('video,audio,video source,audio source');for(var i=0;i<n.length;i++)add(n[i].currentSrc||n[i].src);}
              if(w.MutationObserver)new w.MutationObserver(scan).observe(w.document,{childList:true,subtree:true,attributes:true,attributeFilter:['src']});
              if(w.fetch){var fetch=w.fetch;w.fetch=function(){return fetch.apply(this,arguments).then(function(r){
                var t=r.headers.get('content-type')||'';if(/video|mpegurl|dash\+xml/i.test(t))add(r.url);return r;});};}
              if(w.XMLHttpRequest){var open=w.XMLHttpRequest.prototype.open;w.XMLHttpRequest.prototype.open=function(){
                this.addEventListener('load',function(){try{var t=this.getResponseHeader('content-type')||'';
                  if(/video|mpegurl|dash\+xml/i.test(t)||(!this.responseType||this.responseType==='text')&&/^\s*#EXTM3U/.test(this.responseText))add(this.responseURL);}catch(e){}});
                return open.apply(this,arguments);};}
              w.__kazumiScan=scan;
            }
            w.__kazumiScan();
            if(depth<3)for(var i=0;i<w.frames.length;i++)setup(w.frames[i],depth+1);
          }catch(e){}}
          setup(window,0);
          var urls=[],frames=[],seen=[];
          function collect(w,d){try{var q=w.__kazumiMedia||[];for(var i=0;i<q.length;i++)if(seen.indexOf(q[i])<0&&urls.length<24){seen.push(q[i]);urls.push({url:q[i],referer:w.location.href});}
            var nodes=w.document.querySelectorAll('iframe[src]');for(var k=0;k<nodes.length&&frames.length<24;k++)frames.push({url:nodes[k].src,referer:w.location.href});
            if(d<3)for(var j=0;j<w.frames.length;j++)collect(w.frames[j],d+1);}catch(e){}}
          collect(window,0);
          return JSON.stringify({urls:urls,frames:frames,title:document.title||'',ready:document.readyState});
        })();
    """.trimIndent()
}
